package com.bizzeh.bruce.skills.settings

import java.util.Locale
import kotlin.math.roundToInt

enum class SettingArea(val title: String) {
    DISPLAY("Display"),
    SOUND("Sound"),
    CONNECTIONS("Connections"),
    SYSTEM("System"),
}

/** Values the settings skills read; AndroidSettingsReader on a phone. Null means Android did not say. */
interface SettingsReader {
    fun system(key: String): String?
    fun global(key: String): String?

    /** Current and maximum volume of an AudioManager stream. */
    fun volume(stream: Int): Pair<Int, Int>?
    fun ringerMode(): Int?
    fun interruptionFilter(): Int?
    fun nightMode(): Int?
    fun locationEnabled(): Boolean?
    fun nfcEnabled(): Boolean?
    fun powerSaveMode(): Boolean?
    fun language(): String?
}

/**
 * One setting Bruce knows. The model names settings only by [id]; what is read, and where, is
 * fixed here. [page] is the Android settings action that shows it.
 */
data class SettingEntry(
    val id: String,
    val name: String,
    val area: SettingArea,
    /** Other words people use for it, for search. */
    val words: List<String>,
    val page: String,
    val read: (SettingsReader) -> String?,
)

/** The settings Bruce can find and read (TASK-066), and how each value is shown. */
object SettingsCatalog {
    // Constant values from android.provider.Settings, android.media.AudioManager,
    // android.app.NotificationManager and android.app.UiModeManager, kept as plain values so this
    // file has no Android dependencies.
    private const val STREAM_RING = 2
    private const val STREAM_MUSIC = 3
    private const val STREAM_ALARM = 4
    private const val STREAM_NOTIFICATION = 5

    val entries: List<SettingEntry> = listOf(
        SettingEntry("brightness", "Screen brightness", SettingArea.DISPLAY, listOf("bright", "dim", "light"), "android.settings.DISPLAY_SETTINGS") { r ->
            r.system("screen_brightness")?.toIntOrNull()?.let { "about ${(it * 100 / 255.0).roundToInt()}%" }
        },
        onOff("auto_brightness", "Adaptive brightness", SettingArea.DISPLAY, listOf("automatic brightness"), "android.settings.DISPLAY_SETTINGS") { it.system("screen_brightness_mode") },
        SettingEntry("screen_timeout", "Screen timeout", SettingArea.DISPLAY, listOf("sleep", "screen off", "lock"), "android.settings.DISPLAY_SETTINGS") { r ->
            r.system("screen_off_timeout")?.toLongOrNull()?.let(::duration)
        },
        onOff("auto_rotate", "Auto-rotate screen", SettingArea.DISPLAY, listOf("rotation", "orientation", "landscape", "portrait"), "android.settings.DISPLAY_SETTINGS") { it.system("accelerometer_rotation") },
        SettingEntry("dark_theme", "Dark theme", SettingArea.DISPLAY, listOf("dark mode", "night mode"), "android.settings.DISPLAY_SETTINGS") { r ->
            when (r.nightMode()) {
                1 -> "off"
                2 -> "on"
                0 -> "automatic"
                3 -> "on a custom schedule"
                else -> null
            }
        },
        SettingEntry("font_size", "Font size", SettingArea.DISPLAY, listOf("text size", "font scale"), "android.settings.DISPLAY_SETTINGS") { r ->
            r.system("font_scale")?.toFloatOrNull()?.let { "${(it * 100).roundToInt()}% of the default" }
        },
        volume("ring_volume", "Ring volume", listOf("ringtone", "calls"), STREAM_RING),
        volume("media_volume", "Media volume", listOf("music", "video", "sound"), STREAM_MUSIC),
        volume("alarm_volume", "Alarm volume", listOf("alarm"), STREAM_ALARM),
        volume("notification_volume", "Notification volume", listOf("notifications"), STREAM_NOTIFICATION),
        SettingEntry("sound_mode", "Sound mode", SettingArea.SOUND, listOf("silent", "vibrate", "mute", "ringer"), "android.settings.SOUND_SETTINGS") { r ->
            when (r.ringerMode()) {
                0 -> "silent"
                1 -> "vibrate"
                2 -> "sound on"
                else -> null
            }
        },
        SettingEntry("do_not_disturb", "Do Not Disturb", SettingArea.SOUND, listOf("dnd", "quiet", "interruptions"), "android.settings.SOUND_SETTINGS") { r ->
            when (r.interruptionFilter()) {
                1 -> "off"
                2 -> "on, priority only"
                3 -> "on, total silence"
                4 -> "on, alarms only"
                else -> null
            }
        },
        onOff("touch_vibration", "Vibrate on touch", SettingArea.SOUND, listOf("haptic", "haptics", "feedback"), "android.settings.SOUND_SETTINGS") { it.system("haptic_feedback_enabled") },
        onOff("wifi", "Wi-Fi", SettingArea.CONNECTIONS, listOf("wifi", "wireless", "internet"), "android.settings.WIFI_SETTINGS") { it.global("wifi_on") },
        onOff("bluetooth", "Bluetooth", SettingArea.CONNECTIONS, listOf("headphones", "pairing"), "android.settings.BLUETOOTH_SETTINGS") { it.global("bluetooth_on") },
        onOff("airplane_mode", "Airplane mode", SettingArea.CONNECTIONS, listOf("flight mode", "aeroplane"), "android.settings.AIRPLANE_MODE_SETTINGS") { it.global("airplane_mode_on") },
        // Android does not let an app read whether mobile data is on without the phone permission.
        SettingEntry("mobile_data", "Mobile data", SettingArea.CONNECTIONS, listOf("cellular", "4g", "5g", "data"), "android.settings.DATA_USAGE_SETTINGS") { null },
        onOff("data_roaming", "Data roaming", SettingArea.CONNECTIONS, listOf("roaming", "abroad"), "android.settings.DATA_ROAMING_SETTINGS") { it.global("data_roaming") },
        onOffBoolean("location", "Location", SettingArea.CONNECTIONS, listOf("gps", "location services"), "android.settings.LOCATION_SOURCE_SETTINGS") { it.locationEnabled() },
        onOffBoolean("nfc", "NFC", SettingArea.CONNECTIONS, listOf("contactless", "tap to pay"), "android.settings.NFC_SETTINGS") { it.nfcEnabled() },
        onOffBoolean("battery_saver", "Battery saver", SettingArea.SYSTEM, listOf("power saving", "low power"), "android.settings.BATTERY_SAVER_SETTINGS") { it.powerSaveMode() },
        onOff("auto_time", "Set time automatically", SettingArea.SYSTEM, listOf("clock", "network time", "date"), "android.settings.DATE_SETTINGS") { it.global("auto_time") },
        onOff("auto_time_zone", "Set time zone automatically", SettingArea.SYSTEM, listOf("timezone", "clock"), "android.settings.DATE_SETTINGS") { it.global("auto_time_zone") },
        SettingEntry("time_format", "Time format", SettingArea.SYSTEM, listOf("24-hour", "12-hour", "clock"), "android.settings.DATE_SETTINGS") { r ->
            when (r.system("time_12_24")) {
                "24" -> "24-hour"
                "12" -> "12-hour"
                else -> "follows the language"
            }
        },
        SettingEntry("language", "Language", SettingArea.SYSTEM, listOf("locale", "region"), "android.settings.LOCALE_SETTINGS") { it.language() },
        onOff("developer_options", "Developer options", SettingArea.SYSTEM, listOf("developer"), "android.settings.APPLICATION_DEVELOPMENT_SETTINGS") { it.global("development_settings_enabled") },
        onOff("usb_debugging", "USB debugging", SettingArea.SYSTEM, listOf("adb", "developer"), "android.settings.APPLICATION_DEVELOPMENT_SETTINGS") { it.global("adb_enabled") },
    )

    private val byId = entries.associateBy { it.id }

    operator fun get(id: String): SettingEntry? = byId[id.trim().lowercase(Locale.ROOT)]

    /** Settings matching any word of [query], best first; everything, by area, when [query] is blank. */
    fun find(query: String?): List<SettingEntry> {
        val words = query.orEmpty().lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in IGNORED }
        if (words.isEmpty()) return entries.sortedBy { it.area.ordinal }
        return entries
            .map { entry -> entry to score(entry, words) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(MAX_FOUND)
    }

    fun describe(entry: SettingEntry): String = "${entry.id}: ${entry.name} (${entry.area.title})"

    fun value(entry: SettingEntry, reader: SettingsReader): String? = try {
        entry.read(reader)
    } catch (e: SecurityException) {
        null
    }

    private fun score(entry: SettingEntry, words: List<String>): Int {
        val text = (listOf(entry.id.replace('_', ' '), entry.name, entry.area.title) + entry.words).joinToString(" ").lowercase(Locale.ROOT)
        return words.count { it in text }
    }

    fun duration(millis: Long): String = when {
        millis <= 0 || millis >= Int.MAX_VALUE -> "never"
        millis < 60_000 -> "${millis / 1000} seconds"
        millis % 60_000 == 0L && millis < 3_600_000 -> (millis / 60_000).let { "$it minute${if (it == 1L) "" else "s"}" }
        else -> "${millis / 1000} seconds"
    }

    private fun onOff(id: String, name: String, area: SettingArea, words: List<String>, page: String, raw: (SettingsReader) -> String?) =
        SettingEntry(id, name, area, words, page) { r ->
            when (raw(r)?.trim()) {
                "1" -> "on"
                "0" -> "off"
                else -> null
            }
        }

    private fun onOffBoolean(id: String, name: String, area: SettingArea, words: List<String>, page: String, raw: (SettingsReader) -> Boolean?) =
        SettingEntry(id, name, area, words, page) { r -> raw(r)?.let { if (it) "on" else "off" } }

    private fun volume(id: String, name: String, words: List<String>, stream: Int) =
        SettingEntry(id, name, SettingArea.SOUND, words + "volume", "android.settings.SOUND_SETTINGS") { r ->
            r.volume(stream)?.let { (current, max) -> "$current of $max" }
        }

    private const val MAX_FOUND = 8
    private val IGNORED = setOf("the", "my", "is", "on", "off", "of", "what", "how", "setting", "settings", "phone", "turn", "set")
}
