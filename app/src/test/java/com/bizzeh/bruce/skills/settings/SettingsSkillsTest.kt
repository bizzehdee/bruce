package com.bizzeh.bruce.skills.settings

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for Android's org.json (argument checks) and the real reader. */
@RunWith(RobolectricTestRunner::class)
class SettingsSkillsTest {
    private class ScriptedReader : SettingsReader {
        val system = mutableMapOf<String, String>()
        val global = mutableMapOf<String, String>()
        var volumes = mapOf<Int, Pair<Int, Int>>()
        var ringer: Int? = null
        var filter: Int? = null
        var night: Int? = null
        var location: Boolean? = null
        var nfc: Boolean? = null
        var powerSave: Boolean? = null
        var refuse = false

        override fun system(key: String): String? {
            if (refuse) throw SecurityException("not readable")
            return system[key]
        }

        override fun global(key: String) = global[key]
        override fun volume(stream: Int) = volumes[stream]
        override fun ringerMode() = ringer
        override fun interruptionFilter() = filter
        override fun nightMode() = night
        override fun locationEnabled() = location
        override fun nfcEnabled() = nfc
        override fun powerSaveMode() = powerSave
        override fun language() = "English (United Kingdom)"
    }

    private val reader = ScriptedReader()
    private val skills = SettingsSkills(reader).create().associateBy { it.id }

    private fun run(id: String, vararg arguments: Pair<String, Any>) = runBlocking { skills.getValue(id).execute(SkillArguments(mapOf(*arguments))) }

    private fun text(outcome: SkillOutcome) = (outcome as SkillOutcome.Done).content

    private fun value(id: String) = SettingsCatalog.value(SettingsCatalog[id]!!, reader)

    @Test
    fun findingReadsNothingAndReadingStartsDeclined() {
        assertEquals(SkillState.ACCEPTED, skills.getValue("find_settings").defaultState)
        assertEquals(emptySet<Capability>(), skills.getValue("find_settings").capabilities)
        assertEquals(SkillState.DECLINED, skills.getValue("get_setting").defaultState)
        assertEquals(setOf(Capability.SETTINGS_READ), skills.getValue("get_setting").capabilities)
    }

    @Test
    fun settingsAreFoundByTheirWordsAndListedByAreaWithNoQuery() {
        assertEquals("brightness: Screen brightness (Display)\nauto_brightness: Adaptive brightness (Display)", text(run("find_settings", "query" to "How bright is my screen")).lines().take(2).joinToString("\n"))
        assertTrue(text(run("find_settings", "query" to "turn off wifi")).startsWith("wifi: Wi-Fi (Connections)"))
        assertEquals(listOf("ring_volume", "media_volume", "alarm_volume", "notification_volume"), SettingsCatalog.find("volume").map { it.id })
        assertEquals("No setting matched. Call find_settings with no query to list them all.", text(run("find_settings", "query" to "zebra")))

        val all = text(run("find_settings")).lines()
        assertEquals(SettingsCatalog.entries.size, all.size)
        assertEquals(SettingsCatalog.entries.map { it.area }.sortedBy { it.ordinal }, all.map { line -> SettingArea.entries.first { line.endsWith("(${it.title})") } })
        assertEquals(all, text(run("find_settings", "query" to "the settings")).lines())
    }

    @Test
    fun readingNamesTheValueOrSaysAndroidDidNotTell() {
        reader.system["screen_off_timeout"] = "30000"
        assertEquals("Screen timeout: 30 seconds", text(run("get_setting", "id" to " Screen_Timeout ")))
        assertEquals("Mobile data: Android does not let Bruce read this setting on this phone.", text(run("get_setting", "id" to "mobile_data")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("get_setting", "id" to "secure_android_id") as SkillOutcome.Failed).code)
        reader.refuse = true
        assertEquals("Screen timeout: Android does not let Bruce read this setting on this phone.", text(run("get_setting", "id" to "screen_timeout")))
    }

    @Test
    fun valuesAreShownInPlainWords() {
        reader.system += mapOf("screen_brightness" to "128", "screen_brightness_mode" to "1", "accelerometer_rotation" to "0", "font_scale" to "1.15", "haptic_feedback_enabled" to "x", "time_12_24" to "24")
        reader.global += mapOf("wifi_on" to "1", "bluetooth_on" to "0", "adb_enabled" to "1")
        reader.volumes = mapOf(3 to (7 to 15))
        assertEquals("about 50%", value("brightness"))
        assertEquals("on", value("auto_brightness"))
        assertEquals("off", value("auto_rotate"))
        assertEquals("115% of the default", value("font_size"))
        assertNull("not 0 or 1", value("touch_vibration"))
        assertEquals("on", value("wifi"))
        assertEquals("off", value("bluetooth"))
        assertEquals("on", value("usb_debugging"))
        assertEquals("7 of 15", value("media_volume"))
        assertNull(value("alarm_volume"))
        assertEquals("24-hour", value("time_format"))
        reader.system["time_12_24"] = "12"
        assertEquals("12-hour", value("time_format"))
        reader.system.remove("time_12_24")
        assertEquals("follows the language", value("time_format"))
        assertEquals("English (United Kingdom)", value("language"))

        assertEquals(listOf("automatic", "off", "on", "on a custom schedule", null), listOf(0, 1, 2, 3, 9).map { reader.night = it; value("dark_theme") })
        assertEquals(listOf("silent", "vibrate", "sound on", null), listOf(0, 1, 2, 9).map { reader.ringer = it; value("sound_mode") })
        assertEquals(listOf("off", "on, priority only", "on, total silence", "on, alarms only", null), listOf(1, 2, 3, 4, 0).map { reader.filter = it; value("do_not_disturb") })
        assertEquals(listOf("on", "off", null), listOf(true, false, null).map { reader.location = it; value("location") })
        reader.nfc = true
        reader.powerSave = false
        assertEquals("on", value("nfc"))
        assertEquals("off", value("battery_saver"))

        assertEquals(listOf("never", "never", "15 seconds", "1 minute", "10 minutes", "5400 seconds", "90 seconds"), listOf(0L, Int.MAX_VALUE.toLong(), 15_000L, 60_000L, 600_000L, 5_400_000L, 90_000L).map(SettingsCatalog::duration))
    }

    @Test
    fun theAndroidReaderReadsThePhone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        val audio = context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val android = AndroidSettingsReader(context)

        assertEquals("1 minute", SettingsCatalog.value(SettingsCatalog["screen_timeout"]!!, android))
        assertEquals("on", SettingsCatalog.value(SettingsCatalog["airplane_mode"]!!, android))
        assertEquals("3 of $max", SettingsCatalog.value(SettingsCatalog["alarm_volume"]!!, android))
        for (entry in SettingsCatalog.entries) SettingsCatalog.value(entry, android)
        assertTrue(android.language()!!.isNotEmpty())
    }
}
