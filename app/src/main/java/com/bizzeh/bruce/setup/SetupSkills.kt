package com.bizzeh.bruce.setup

import androidx.annotation.StringRes
import com.bizzeh.bruce.R
import com.bizzeh.bruce.runtime.BruceRuntime
import com.bizzeh.bruce.skills.CapabilityClass
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillState

/** One switch of the wizard's skills step (TASK-112): every skill of one kind, and whether any is on. */
data class SkillGroup(val kind: CapabilityClass, val skills: List<Skill>, val on: Boolean)

/** The wizard's skills step: kinds of skills, the state a switch gives them, and what they cost in context. */
object SetupSkills {
    /** Above this share of the context, the system prompt crowds the chat (owner, 2026-10-02). */
    const val WARNING_SHARE = 0.25

    /** Tool definitions are mostly JSON, which tokenises at about three characters a token. */
    private const val CHARS_PER_TOKEN = 3

    /** A skill with no capabilities (`find_settings`) needs nothing and runs automatically, the same as the INFORMATION skills below. */
    fun kind(skill: Skill): CapabilityClass = skill.capabilities.minOfOrNull { it.capabilityClass } ?: CapabilityClass.INFORMATION

    fun groups(skills: List<Skill>, states: Map<String, SkillState>): List<SkillGroup> =
        skills.groupBy(::kind).toSortedMap().map { (kind, members) -> SkillGroup(kind, members, members.any { states[it.id] != SkillState.DECLINED }) }

    /**
     * A switched-on skill's state: the phone-status skills run without asking, as the automatic
     * skills of a fresh install do; everything else asks each time, high-risk skills included.
     */
    fun onState(skill: Skill): SkillState = if (kind(skill) == CapabilityClass.INFORMATION && !skill.highRisk) SkillState.ACCEPTED else SkillState.ASK

    /**
     * The system prompt's size with [enabled] skills offered, estimated from its text: the wizard
     * runs before a model is loaded, so the model's own count is not available.
     */
    fun promptTokens(personality: String, enabled: List<Skill>): Int {
        val guidance = if (enabled.isEmpty()) BruceRuntime.NO_SKILLS_GUIDANCE else BruceRuntime.GUIDANCE
        val tools = enabled.sumOf { it.id.length + it.description.length + it.input.toJson().toString().length }
        return (personality.length + guidance.length + tools) / CHARS_PER_TOKEN
    }

    fun crowded(promptTokens: Int, contextLength: Int): Boolean = promptTokens > contextLength * WARNING_SHARE
}

/** Names for the kinds of skills. */
object SetupText {
    @StringRes
    fun kind(kind: CapabilityClass): Int = when (kind) {
        CapabilityClass.INFORMATION -> R.string.setup_skills_information
        CapabilityClass.FILES -> R.string.setup_skills_files
        CapabilityClass.PERSONAL_DATA -> R.string.setup_skills_personal
        CapabilityClass.SENSORS -> R.string.setup_skills_sensors
        CapabilityClass.COMMUNICATION -> R.string.setup_skills_communication
        CapabilityClass.SYSTEM -> R.string.setup_skills_system
        CapabilityClass.NETWORK -> R.string.setup_skills_network
    }
}
