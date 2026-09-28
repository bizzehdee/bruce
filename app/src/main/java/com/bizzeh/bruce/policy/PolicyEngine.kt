package com.bizzeh.bruce.policy

import com.bizzeh.bruce.skills.Denial
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Resolution
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Whether a skill request's targets lie inside what the user granted. */
sealed interface ScopeCheck {
    data object InScope : ScopeCheck

    /** [message] names the problem for the model without repeating its paths. */
    data class OutOfScope(val message: String) : ScopeCheck
}

sealed interface PolicyDecision {
    /** May run now. Only [PolicyEngine] creates one, so nothing can run a skill without passing policy. */
    class Allowed internal constructor(val request: SkillRequest, val policyVersion: Long) : PolicyDecision

    /** The skill is in the Ask state: the user must approve this exact operation first (TASK-041). */
    data class NeedsConfirmation(val request: SkillRequest, val policyVersion: Long) : PolicyDecision

    data class Denied(val denial: Denial) : PolicyDecision
}

/**
 * Decides whether the model's skill request may run, in the order of product spec §25: tool and
 * arguments, the user's skill state, Android permissions, granted scope (in every state), then
 * confirmation for skills in the Ask state. The model is never the security boundary: its request
 * is only ever input to this check.
 */
class PolicyEngine(
    private val registry: SkillRegistry,
    private val states: SkillStateStore,
    private val output: ToolOutput,
    private val permissionGranted: (String) -> Boolean,
    /** Folder grants arrive in TASK-040; until then a skill that needs them has none. */
    private val scope: suspend (SkillRequest) -> ScopeCheck = { ScopeCheck.OutOfScope("No files or folders have been granted.") },
) {
    suspend fun decide(tool: String, rawArguments: String): PolicyDecision {
        val request = when (val resolution = registry.resolve(tool, rawArguments)) {
            is Resolution.Refused -> return PolicyDecision.Denied(resolution.denial)
            is Resolution.Resolved -> resolution.request
        }
        val skill = request.skill
        val state = states.state(skill)
        if (state == SkillState.DECLINED) {
            return deny(request, DenialCode.CAPABILITY_DISABLED, "The user has turned this skill off.", userCanChange = true)
        }
        if (!skill.androidPermissions.all(permissionGranted)) {
            return deny(request, DenialCode.ANDROID_PERMISSION_DENIED, "Android has not granted a permission this skill needs.", userCanChange = true)
        }
        if (skill.scope != ResourceScope.NONE) {
            val check = scope(request)
            if (check is ScopeCheck.OutOfScope) return deny(request, DenialCode.RESOURCE_OUTSIDE_SCOPE, check.message, userCanChange = true)
        }
        val version = states.policyVersion()
        return if (state == SkillState.ASK) PolicyDecision.NeedsConfirmation(request, version) else PolicyDecision.Allowed(request, version)
    }

    /** Runs an allowed request and returns what goes back to the model: a sanitised result or a denial. */
    suspend fun execute(allowed: PolicyDecision.Allowed): JSONObject {
        val skill = allowed.request.skill
        val outcome = try {
            skill.execute(allowed.request.arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The exception text may carry file contents or paths; the model only learns that it failed.
            SkillOutcome.Failed(DenialCode.TOOL_FAILED, "The skill failed.", retryable = false)
        }
        return output.result(skill.id, outcome)
    }

    fun refusal(denied: PolicyDecision.Denied): JSONObject = output.denial(denied.denial)

    private fun deny(request: SkillRequest, code: DenialCode, message: String, userCanChange: Boolean) =
        PolicyDecision.Denied(Denial(code, request.skill.id, message, userCanChange = userCanChange, retryable = false))
}
