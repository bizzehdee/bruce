package com.bizzeh.bruce.skills

/** Broad groups of what a skill may touch (product spec §22). */
enum class CapabilityClass { INFORMATION, FILES, PERSONAL_DATA, SENSORS, COMMUNICATION, SYSTEM, NETWORK }

/** What a skill needs. The policy engine (TASK-035) decides whether a skill with these may run. */
enum class Capability(val capabilityClass: CapabilityClass) {
    DEVICE(CapabilityClass.INFORMATION),
    BATTERY(CapabilityClass.INFORMATION),
    TIME(CapabilityClass.INFORMATION),
    STORAGE_STATUS(CapabilityClass.INFORMATION),
    NETWORK_STATUS(CapabilityClass.INFORMATION),
    FILE_READ(CapabilityClass.FILES),
    FILE_CREATE(CapabilityClass.FILES),
    FILE_WRITE(CapabilityClass.FILES),
    FILE_MOVE(CapabilityClass.FILES),
    FILE_DELETE(CapabilityClass.FILES),
    CONTACTS(CapabilityClass.PERSONAL_DATA),
    CALENDAR_READ(CapabilityClass.PERSONAL_DATA),
    CALENDAR_WRITE(CapabilityClass.PERSONAL_DATA),
    LOCATION(CapabilityClass.SENSORS),
    CAMERA(CapabilityClass.SENSORS),
    MICROPHONE(CapabilityClass.SENSORS),
    MOTION(CapabilityClass.SENSORS),
    SHARE(CapabilityClass.COMMUNICATION),
    SMS(CapabilityClass.COMMUNICATION),
    EMAIL(CapabilityClass.COMMUNICATION),
    PHONE(CapabilityClass.COMMUNICATION),
    APP_LAUNCH(CapabilityClass.SYSTEM),
    SETTINGS_READ(CapabilityClass.SYSTEM),
    SETTINGS_WRITE(CapabilityClass.SYSTEM),
    WEB_SEARCH(CapabilityClass.NETWORK),
    HTTP_READ(CapabilityClass.NETWORK),
    HTTP_WRITE(CapabilityClass.NETWORK),
}

/** The user's choice for a skill (plan.md, Skill states). */
enum class SkillState {
    /** Never used. */
    DECLINED,
    /** The user approves each exact operation. */
    ASK,
    /** Always allowed; scope checks still apply. */
    ACCEPTED,
}

/** Which resources a skill acts on, for the scope check. */
enum class ResourceScope {
    /** Nothing outside the phone's own status. */
    NONE,
    /** Only files and folders the user granted through the Storage Access Framework. */
    GRANTED_FILES,
}

/** What a skill's execution produced, before sanitising. */
sealed interface SkillOutcome {
    data class Done(val content: String) : SkillOutcome

    /** The skill ran and could not do it; [code] is one of the spec's failure codes. */
    data class Failed(val code: DenialCode, val message: String, val retryable: Boolean = false) : SkillOutcome
}

/**
 * One skill (product spec §21). [id] is what the model names; [execute] receives arguments that
 * have already been validated against [input], and is called only by the runtime after policy.
 */
class Skill(
    val id: String,
    val version: Int,
    val description: String,
    val input: InputSchema,
    val capabilities: Set<Capability>,
    val defaultState: SkillState,
    val highRisk: Boolean = false,
    val scope: ResourceScope = ResourceScope.NONE,
    val androidPermissions: List<String> = emptyList(),
    val execute: suspend (SkillArguments) -> SkillOutcome,
) {
    init {
        require(ID.matches(id)) { "skill id must be lower-case words joined by underscores: $id" }
        require(version > 0) { "version must be positive" }
        require(description.isNotBlank()) { "description is required" }
    }

    private companion object {
        val ID = Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*")
    }
}
