package com.bizzeh.bruce.skills.camera

import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Asks the screen to open the camera and waits for the user's photo: the stored image's name, or
 * null if they closed the camera. The activity watches [pending], opens the camera, and answers
 * with [complete].
 */
class PhotoRequests(private val wait: Duration = WAIT) {
    private val waiting = MutableStateFlow<CompletableDeferred<String?>?>(null)
    val pending: StateFlow<CompletableDeferred<String?>?> = waiting.asStateFlow()

    suspend fun take(): String? {
        val answer = CompletableDeferred<String?>()
        waiting.value = answer
        return try {
            withTimeoutOrNull(wait) { answer.await() }
        } finally {
            waiting.compareAndSet(answer, null)
        }
    }

    fun complete(name: String?) {
        waiting.value?.complete(name)
    }

    companion object {
        /** Inside the runtime's three-minute turn limit, with room for the reply. */
        val WAIT = 2.minutes
    }
}

/**
 * The camera skill (TASK-093): opens the camera, and the user takes the photo, an explicit action
 * every time. The photo waits with the next message, as a picked image does (TASK-082), so the user
 * sends it with their question. Off by default; no camera permission, as the camera app takes the
 * picture. There is no microphone skill: voice input covers speaking (owner, 2026-09-30).
 */
class CameraSkills(private val photos: PhotoRequests, private val onScreen: () -> Boolean) {
    fun create(): List<Skill> = listOf(takePhoto())

    private fun takePhoto() = Skill(
        id = "take_photo",
        version = 1,
        description = "Open the camera so the user can take a photo to show you. They send it with their next message.",
        input = InputSchema(emptyList()),
        capabilities = setOf(Capability.CAMERA),
        defaultState = SkillState.DECLINED,
    ) {
        if (!onScreen()) return@Skill SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Bruce is not on screen, and Android only lets it open the camera while it is. Tell the user instead.")
        when (photos.take()) {
            null -> SkillOutcome.Done("The user closed the camera without taking a photo.")
            else -> SkillOutcome.Done("The user took a photo. It is attached to their next message; ask them to send it with what they want to know.")
        }
    }
}
