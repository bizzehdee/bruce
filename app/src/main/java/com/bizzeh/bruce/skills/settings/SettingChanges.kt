package com.bizzeh.bruce.skills.settings

import java.util.Locale
import kotlin.math.roundToInt

/** How a changeable setting takes a new value; only these kinds exist, and only for catalog entries. */
sealed interface SettingChange {
    /** A `Settings.System` on/off integer. */
    data class Switch(val key: String) : SettingChange

    /** `Settings.System.SCREEN_BRIGHTNESS`, 0–255, given as a percentage. */
    data object Brightness : SettingChange

    /** `Settings.System.SCREEN_OFF_TIMEOUT`, given in seconds, one of Android's usual choices. */
    data object Timeout : SettingChange

    /** An AudioManager stream's volume, 0 to its maximum. */
    data class Volume(val stream: Int) : SettingChange
}

/** What is written. System settings need the user's "Modify system settings" grant; volumes do not. */
sealed interface SettingWrite {
    data class System(val key: String, val value: Int) : SettingWrite

    data class Volume(val stream: Int, val level: Int) : SettingWrite
}

/** Changes settings and opens settings pages; AndroidSettingsWriter on a phone. */
interface SettingsWriter {
    /** Whether the user has granted Bruce "Modify system settings". */
    fun canWriteSystem(): Boolean
    fun write(write: SettingWrite): Boolean

    /** Opens an Android settings page for the user; false if Android would not show it. */
    fun open(page: String): Boolean
}

sealed interface ChangePlan {
    /** [shown] is the new value as the setting's value reads. */
    data class Planned(val entry: SettingEntry, val write: SettingWrite, val shown: String) : ChangePlan

    data class Invalid(val reason: String) : ChangePlan
}

/** Turns the model's requested value into an exact write, or says why it cannot be done (TASK-067). */
object SettingChanges {
    val TIMEOUT_SECONDS = listOf(15, 30, 60, 120, 300, 600, 1800)

    fun plan(entry: SettingEntry, value: String, reader: SettingsReader): ChangePlan {
        val change = entry.change ?: return ChangePlan.Invalid("Bruce cannot change ${entry.name}. Use open_settings_page so the user can change it.")
        val text = value.trim().lowercase(Locale.ROOT)
        return when (change) {
            is SettingChange.Switch -> when (text) {
                "on", "true", "1", "yes" -> ChangePlan.Planned(entry, SettingWrite.System(change.key, 1), "on")
                "off", "false", "0", "no" -> ChangePlan.Planned(entry, SettingWrite.System(change.key, 0), "off")
                else -> ChangePlan.Invalid("${entry.name} takes \"on\" or \"off\".")
            }
            SettingChange.Brightness -> number(text)?.takeIf { it in 1..100 }
                ?.let { ChangePlan.Planned(entry, SettingWrite.System("screen_brightness", (it * 255 / 100.0).roundToInt()), "about $it%") }
                ?: ChangePlan.Invalid("${entry.name} takes a percentage from 1 to 100.")
            SettingChange.Timeout -> number(text)?.takeIf { it in TIMEOUT_SECONDS }
                ?.let { ChangePlan.Planned(entry, SettingWrite.System("screen_off_timeout", it * 1000), SettingsCatalog.duration(it * 1000L)) }
                ?: ChangePlan.Invalid("${entry.name} takes one of these numbers of seconds: ${TIMEOUT_SECONDS.joinToString()}.")
            is SettingChange.Volume -> {
                val max = reader.volume(change.stream)?.second ?: return ChangePlan.Invalid("Android did not give the range of ${entry.name}.")
                number(text)?.takeIf { it in 0..max }
                    ?.let { ChangePlan.Planned(entry, SettingWrite.Volume(change.stream, it), "$it of $max") }
                    ?: ChangePlan.Invalid("${entry.name} takes a level from 0 to $max.")
            }
        }
    }

    /** A whole number, allowing a trailing "%" or "s". */
    private fun number(text: String): Int? = text.removeSuffix("%").removeSuffix("s").trim().toIntOrNull()
}
