package com.bizzeh.bruce.skills.location

import android.Manifest
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import java.util.Locale
import kotlin.math.roundToInt

/** One position; [accuracyMetres] is Android's estimate, when it gives one, and [ageSeconds] how old it is. */
data class Reading(val latitude: Double, val longitude: Double, val accuracyMetres: Float?, val ageSeconds: Long = 0)

/** The phone's location, one reading at a time; AndroidLocationSource on a phone. */
interface LocationSource {
    /** Whether location is turned on in the phone's settings. */
    fun enabled(): Boolean

    /** Whether the user allowed a precise location rather than an approximate one. */
    fun precise(): Boolean

    /** One reading, or null if none came in time. */
    suspend fun current(): Reading?

    /** The latest position Android already holds, however old; null if none. */
    fun lastKnown(): Reading?
}

/**
 * The location skill (TASK-091): one reading when asked, never tracking. Off by default and listed
 * with the privacy-sensitive skills. Approximate unless the user allowed precise location when
 * Android asked; with approximate only, the reading is rounded to about a kilometre.
 */
class LocationSkills(private val source: LocationSource, private val onScreen: () -> Boolean = { true }) {
    fun create(): List<Skill> = listOf(where())

    private fun where() = Skill(
        id = "get_location",
        version = 1,
        description = "Get the phone's current location as latitude and longitude, once.",
        input = InputSchema(emptyList()),
        capabilities = setOf(Capability.LOCATION),
        defaultState = SkillState.DECLINED,
        androidPermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
        // Asked for together, so Android's dialog lets the user choose approximate or precise.
        optionalPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
    ) {
        // Android grants location to an app only while it is in the foreground (app-op "foreground").
        if (!onScreen()) return@Skill SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Android only lets Bruce find the location while Bruce is on screen.")
        if (!source.enabled()) return@Skill SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "Location is turned off in the phone's settings.")
        val reading = source.current()
            ?: source.lastKnown()?.takeIf { it.ageSeconds <= MAX_AGE_SECONDS }
            ?: return@Skill SkillOutcome.Failed(DenialCode.TOOL_FAILED, "The phone could not find its location in time.", retryable = true)
        SkillOutcome.Done(describe(reading, source.precise()))
    }

    companion object {
        /** An older position could be somewhere the user has since left. */
        const val MAX_AGE_SECONDS = 15 * 60L
        private const val STALE_SECONDS = 60L

        /** Two decimal places is about a kilometre, the most an approximate reading claims. */
        fun describe(reading: Reading, precise: Boolean): String {
            val places = if (precise) 5 else 2
            val accuracy = reading.accuracyMetres?.let { metres ->
                if (metres >= 1000 || !precise) " (to within about ${maxOf(1, (maxOf(metres, 1000f) / 1000).roundToInt())} km)" else " (to within about ${metres.roundToInt()} m)"
            }.orEmpty()
            val age = if (reading.ageSeconds >= STALE_SECONDS) " As of about ${maxOf(1, reading.ageSeconds / 60)} minutes ago." else ""
            return "Latitude %.${places}f, longitude %.${places}f$accuracy.$age".format(Locale.ROOT, reading.latitude, reading.longitude)
        }
    }
}
