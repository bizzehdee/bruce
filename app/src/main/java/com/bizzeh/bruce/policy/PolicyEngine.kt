package com.bizzeh.bruce.policy

import com.bizzeh.bruce.skills.Denial
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Resolution
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** A resource a skill request acts on: [display] is shown to the user, [identity] is what an approval binds to. */
data class ResourceTarget(val display: String, val identity: String)

/** Whether a skill request's targets lie inside what the user granted. */
sealed interface ScopeCheck {
    data class InScope(val targets: List<ResourceTarget> = emptyList()) : ScopeCheck

    /** [message] names the problem for the model without repeating its paths. */
    data class OutOfScope(val message: String) : ScopeCheck
}

sealed interface PolicyDecision {
    /** May run now. Only [PolicyEngine] creates one, so nothing can run a skill without passing policy. */
    class Allowed internal constructor(val request: SkillRequest, val policyVersion: Long) : PolicyDecision

    /**
     * The skill is in the Ask state: the user must approve this exact operation first. [confirm]
     * binds the approval to the request, its [targets], the [policyVersion] and [askedAt].
     */
    data class NeedsConfirmation(
        val request: SkillRequest,
        val policyVersion: Long,
        val targets: List<ResourceTarget>,
        val askedAt: Long,
        /** Sites the call reaches that the user has not approved (TASK-068); the card can always allow them. */
        val newSites: List<String> = emptyList(),
        /** Set by the chat, never the model, when the user chose to always allow [newSites]. */
        val rememberSites: Boolean = false,
    ) : PolicyDecision

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
    /** Checks a file skill's target against the user's grants (GrantScope.check). */
    private val scope: suspend (SkillRequest) -> ScopeCheck,
    /** Whether a skill's requirement holds; a skill whose requirement does not is locked off. */
    private val requirementMet: suspend (SkillRequirement) -> Boolean = { true },
    /** Which sites web skills may reach; without it, none. */
    private val sites: SiteAccess? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun decide(tool: String, rawArguments: String): PolicyDecision = when (val resolution = registry.resolve(tool, rawArguments)) {
        is Resolution.Refused -> PolicyDecision.Denied(resolution.denial)
        is Resolution.Resolved -> evaluate(resolution.request)
    }

    /**
     * The user approved [pending]. Allowed only if deciding again now gives the same request the same
     * way: still Ask, same policy version (no state or grant changed), same targets, and not expired.
     */
    suspend fun confirm(pending: PolicyDecision.NeedsConfirmation): PolicyDecision {
        val request = pending.request
        if (clock() - pending.askedAt > CONFIRMATION_LIFETIME_MS) {
            return deny(request, DenialCode.CONFIRMATION_REQUIRED, "The user's approval came too late and has expired.", userCanChange = false, retryable = true)
        }
        return when (val now = evaluate(request)) {
            is PolicyDecision.Denied -> now
            is PolicyDecision.NeedsConfirmation ->
                if (now.policyVersion == pending.policyVersion && now.targets == pending.targets) {
                    if (pending.rememberSites) sites?.approve(now.newSites)
                    PolicyDecision.Allowed(request, now.policyVersion)
                } else {
                    changed(request)
                }
            is PolicyDecision.Allowed -> changed(request)
        }
    }

    /** The user said no to [pending]. */
    fun declined(pending: PolicyDecision.NeedsConfirmation): PolicyDecision.Denied =
        deny(pending.request, DenialCode.USER_DENIED, "The user declined this.", userCanChange = false)

    private fun changed(request: SkillRequest) =
        deny(request, DenialCode.CONFIRMATION_REQUIRED, "Settings or files changed after the user was asked, so the approval no longer applies.", userCanChange = false, retryable = true)

    private suspend fun evaluate(request: SkillRequest): PolicyDecision {
        val skill = request.skill
        val state = states.state(skill)
        if (state == SkillState.DECLINED) {
            return deny(request, DenialCode.CAPABILITY_DISABLED, "The user has turned this skill off.", userCanChange = true)
        }
        skill.requires?.takeUnless { requirementMet(it) }?.let { requirement ->
            val why = when (requirement) {
                SkillRequirement.FILE_GRANT -> "it needs a file or folder granted in Settings, Permissions"
                SkillRequirement.NETWORK_ALLOWED -> "the network mode in Settings is Offline"
            }
            return deny(request, DenialCode.CAPABILITY_DISABLED, "This skill is locked off: $why.", userCanChange = true)
        }
        if (!skill.androidPermissions.all(permissionGranted)) {
            return deny(request, DenialCode.ANDROID_PERMISSION_DENIED, "Android has not granted a permission this skill needs.", userCanChange = true)
        }
        var newSites = emptyList<String>()
        val targets = when (skill.scope) {
            ResourceScope.NONE -> emptyList()
            ResourceScope.WEB -> {
                val host = skill.site?.invoke(request.arguments)
                    ?: return deny(request, DenialCode.INVALID_ARGUMENTS, "This skill needs an http or https address.", userCanChange = false, retryable = true)
                when (sites?.rule(host) ?: SiteRule.BLOCKED) {
                    SiteRule.BLOCKED -> return deny(request, DenialCode.NETWORK_DISABLED, "The network mode in Settings does not allow web access.", userCanChange = true)
                    SiteRule.ASK -> newSites = listOf(host)
                    SiteRule.ALLOWED -> Unit
                }
                listOf(ResourceTarget(host, "site:$host"))
            }
            else -> when (val check = scope(request)) {
                is ScopeCheck.OutOfScope -> return deny(request, DenialCode.RESOURCE_OUTSIDE_SCOPE, check.message, userCanChange = true)
                is ScopeCheck.InScope -> check.targets
            }
        }
        val version = states.policyVersion()
        // A site the user has not approved is asked about even when the skill itself is Accepted.
        return if (state == SkillState.ASK || newSites.isNotEmpty()) {
            PolicyDecision.NeedsConfirmation(request, version, targets, clock(), newSites)
        } else {
            PolicyDecision.Allowed(request, version)
        }
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

    private fun deny(request: SkillRequest, code: DenialCode, message: String, userCanChange: Boolean, retryable: Boolean = false) =
        PolicyDecision.Denied(Denial(code, request.skill.id, message, userCanChange = userCanChange, retryable = retryable))

    companion object {
        /** How long an approval card stays valid; afterwards the model must ask again. */
        const val CONFIRMATION_LIFETIME_MS = 15 * 60 * 1000L
    }
}
