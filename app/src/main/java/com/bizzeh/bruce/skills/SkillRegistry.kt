package com.bizzeh.bruce.skills

import org.json.JSONObject

/** A request the model made that names a known skill with valid arguments; policy decides whether it runs. */
data class SkillRequest(val skill: Skill, val arguments: SkillArguments)

sealed interface Resolution {
    data class Resolved(val request: SkillRequest) : Resolution

    data class Refused(val denial: Denial) : Resolution
}

/**
 * Every skill Bruce has. The model's tool name and arguments are untrusted: [resolve] refuses an
 * unknown name or arguments that do not match the skill's schema before anything else looks at them.
 */
class SkillRegistry(skills: List<Skill>) {
    private val byId: Map<String, Skill> = skills.associateBy { it.id }

    init {
        require(byId.size == skills.size) { "skill ids must be unique" }
    }

    val skills: Collection<Skill> get() = byId.values

    operator fun get(id: String): Skill? = byId[id]

    fun resolve(tool: String, rawArguments: String): Resolution {
        val skill = byId[tool] ?: return Resolution.Refused(
            Denial(DenialCode.UNKNOWN_TOOL, ToolOutput.plain(tool, MAX_ECHOED_NAME), "No skill has this name.", userCanChange = false, retryable = false),
        )
        return when (val check = skill.input.check(rawArguments)) {
            is ArgumentCheck.Valid -> Resolution.Resolved(SkillRequest(skill, check.arguments))
            is ArgumentCheck.Invalid -> Resolution.Refused(
                Denial(DenialCode.INVALID_ARGUMENTS, skill.id, check.reason, userCanChange = false, retryable = true),
            )
        }
    }

    /** The short list the model sees up front: one line per skill (TASK-033's index format). */
    fun index(skills: Collection<Skill> = this.skills): List<String> =
        skills.map { skill -> "${skill.id}(${skill.input.parameters.joinToString(", ") { it.name }}): ${skill.description}" }

    /** The full description of one skill, for when the model is about to use it. */
    fun describe(skill: Skill): JSONObject = JSONObject()
        .put("name", skill.id)
        .put("description", skill.description)
        .put("parameters", skill.input.toJson())

    private companion object {
        const val MAX_ECHOED_NAME = 64
    }
}
