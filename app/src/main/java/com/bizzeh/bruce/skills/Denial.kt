package com.bizzeh.bruce.skills

import org.json.JSONObject

/** Why a skill request did not run, or failed (product spec §28). */
enum class DenialCode {
    UNKNOWN_TOOL,
    INVALID_ARGUMENTS,
    CAPABILITY_DISABLED,
    CAPABILITY_NOT_GRANTED,
    RESOURCE_OUTSIDE_SCOPE,
    ANDROID_PERMISSION_DENIED,
    OS_RESTRICTION,
    USER_DENIED,
    CONFIRMATION_REQUIRED,
    NETWORK_DISABLED,
    NETWORK_DOMAIN_BLOCKED,
    RESOURCE_NOT_FOUND,
    TOOL_UNAVAILABLE,
    TOOL_FAILED,
    RATE_LIMITED,
}

/**
 * A structured refusal returned to the model in place of a result. [userCanChange] says whether a
 * setting could allow it; [retryable] whether trying again unchanged might work.
 */
data class Denial(
    val code: DenialCode,
    val tool: String,
    val message: String,
    val userCanChange: Boolean,
    val retryable: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", "denied")
        .put("code", code.name)
        .put("tool", tool)
        .put("message", message)
        .put("user_can_change", userCanChange)
        .put("retryable", retryable)
}
