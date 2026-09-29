package com.bizzeh.bruce.skills.settings

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import android.content.Intent
import com.bizzeh.bruce.policy.ResourceTarget
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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

    private class ScriptedWriter : SettingsWriter {
        var granted = true
        var refuse = false
        var pages = true
        val writes = mutableListOf<SettingWrite>()
        val opened = mutableListOf<String>()

        override fun canWriteSystem() = granted

        override fun write(write: SettingWrite): Boolean {
            if (refuse) return false
            writes += write
            return true
        }

        override fun open(page: String): Boolean {
            opened += page
            return pages
        }
    }

    private val reader = ScriptedReader()
    private val writer = ScriptedWriter()
    private val settings = SettingsSkills(reader, writer)
    private val skills = settings.create().associateBy { it.id }

    private fun run(id: String, vararg arguments: Pair<String, Any>) = runBlocking { skills.getValue(id).execute(SkillArguments(mapOf(*arguments))) }

    private fun text(outcome: SkillOutcome) = (outcome as SkillOutcome.Done).content

    private fun value(id: String) = SettingsCatalog.value(SettingsCatalog[id]!!, reader)

    @Test
    fun allStartDeclined() {
        assertEquals(listOf(SkillState.DECLINED), skills.values.map { it.defaultState }.distinct())
        assertEquals(emptySet<Capability>(), skills.getValue("find_settings").capabilities)
        assertEquals(setOf(Capability.SETTINGS_READ), skills.getValue("get_setting").capabilities)
        assertEquals(ResourceScope.PHONE_SETTINGS, skills.getValue("set_setting").scope)
        assertEquals(setOf(Capability.SETTINGS_WRITE), skills.getValue("set_setting").capabilities)
    }

    private fun plan(id: String, value: String) = SettingChanges.plan(SettingsCatalog[id]!!, value, reader)

    @Test
    fun changesAreCheckedAndTurnedIntoExactWrites() {
        reader.volumes = mapOf(4 to (3 to 7))
        assertEquals(SettingWrite.System("accelerometer_rotation", 1), (plan("auto_rotate", " ON ") as ChangePlan.Planned).write)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 0), listOf("true", "1", "yes", "off", "false", "0", "no").map { ((plan("auto_brightness", it) as ChangePlan.Planned).write as SettingWrite.System).value })
        assertEquals("Auto-rotate screen takes \"on\" or \"off\".", (plan("auto_rotate", "sideways") as ChangePlan.Invalid).reason)
        assertEquals(ChangePlan.Planned(SettingsCatalog["brightness"]!!, SettingWrite.System("screen_brightness", 102), "about 40%"), plan("brightness", "40%"))
        assertTrue(plan("brightness", "0") is ChangePlan.Invalid)
        assertTrue(plan("brightness", "bright") is ChangePlan.Invalid)
        assertEquals(ChangePlan.Planned(SettingsCatalog["screen_timeout"]!!, SettingWrite.System("screen_off_timeout", 60_000), "1 minute"), plan("screen_timeout", "60s"))
        assertEquals("Screen timeout takes one of these numbers of seconds: 15, 30, 60, 120, 300, 600, 1800.", (plan("screen_timeout", "45") as ChangePlan.Invalid).reason)
        assertEquals(ChangePlan.Planned(SettingsCatalog["alarm_volume"]!!, SettingWrite.Volume(4, 7), "7 of 7"), plan("alarm_volume", "7"))
        assertEquals("Alarm volume takes a level from 0 to 7.", (plan("alarm_volume", "8") as ChangePlan.Invalid).reason)
        assertEquals("Android did not give the range of Ring volume.", (plan("ring_volume", "2") as ChangePlan.Invalid).reason)
        assertEquals("Bruce cannot change Wi-Fi. Use open_settings_page so the user can change it.", (plan("wifi", "on") as ChangePlan.Invalid).reason)
    }

    private fun request(id: String, value: String) = SkillRequest(skills.getValue("set_setting"), SkillArguments(mapOf("id" to id, "value" to value)))

    @Test
    fun theUserIsAskedAboutTheExactChangeOnlyWhenItCanBeMade() {
        reader.system["screen_off_timeout"] = "30000"
        val check = settings.check(request("screen_timeout", "60")) as ScopeCheck.InScope
        assertEquals(listOf(ResourceTarget("Screen timeout: 30 seconds → 1 minute", "setting:screen_timeout:System(key=screen_off_timeout, value=60000)")), check.targets)
        assertEquals("unknown", (settings.check(request("auto_rotate", "on")) as ScopeCheck.InScope).targets.single().display.substringAfter(": ").substringBefore(" →"))
        assertTrue((settings.check(request("wifi", "on")) as ScopeCheck.OutOfScope).message.contains("open_settings_page"))
        assertEquals("Unknown setting id. Find it with find_settings.", (settings.check(request("android_id", "1")) as ScopeCheck.OutOfScope).message)

        writer.granted = false
        assertTrue((settings.check(request("screen_timeout", "60")) as ScopeCheck.OutOfScope).message.contains("Permissions"))
        reader.volumes = mapOf(3 to (2 to 15))
        assertTrue("volumes need no grant", settings.check(request("media_volume", "5")) is ScopeCheck.InScope)
    }

    @Test
    fun changingWritesOnlyWhatWasPlanned() {
        assertEquals(SkillOutcome.Done("Screen brightness is now about 40%."), run("set_setting", "id" to "brightness", "value" to "40"))
        assertEquals(listOf<SettingWrite>(SettingWrite.System("screen_brightness", 102)), writer.writes)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("set_setting", "id" to "brightness", "value" to "400") as SkillOutcome.Failed).code)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("set_setting", "id" to "nope", "value" to "1") as SkillOutcome.Failed).code)
        writer.refuse = true
        assertEquals(SkillOutcome.Failed(DenialCode.TOOL_FAILED, "Android did not change Screen brightness."), run("set_setting", "id" to "brightness", "value" to "40"))
        writer.granted = false
        assertEquals(DenialCode.ANDROID_PERMISSION_DENIED, (run("set_setting", "id" to "brightness", "value" to "40") as SkillOutcome.Failed).code)
        assertEquals(1, writer.writes.size)
    }

    @Test
    fun settingsPagesOpenForTheUser() {
        assertEquals(SkillOutcome.Done("Opened the phone's settings page for Wi-Fi. The user changes it there."), run("open_settings_page", "id" to "wifi"))
        assertEquals(listOf("android.settings.WIFI_SETTINGS"), writer.opened)
        writer.pages = false
        assertEquals(DenialCode.TOOL_FAILED, (run("open_settings_page", "id" to "bluetooth") as SkillOutcome.Failed).code)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("open_settings_page", "id" to "nope") as SkillOutcome.Failed).code)
        assertEquals("brightness: Screen brightness (Display, Bruce can change it)", SettingsCatalog.describe(SettingsCatalog["brightness"]!!))
    }

    @Test
    fun settingsAreFoundByTheirWordsAndListedByAreaWithNoQuery() {
        assertEquals(listOf("brightness", "auto_brightness"), text(run("find_settings", "query" to "How bright is my screen")).lines().take(2).map { it.substringBefore(":") })
        assertTrue(text(run("find_settings", "query" to "turn off wifi")).startsWith("wifi: Wi-Fi (Connections)"))
        assertEquals(listOf("ring_volume", "media_volume", "alarm_volume", "notification_volume"), SettingsCatalog.find("volume").map { it.id })
        assertEquals("No setting matched. Call find_settings with no query to list them all.", text(run("find_settings", "query" to "zebra")))

        val all = text(run("find_settings")).lines()
        assertEquals(SettingsCatalog.entries.size, all.size)
        assertEquals(SettingsCatalog.entries.map { it.area }.sortedBy { it.ordinal }, all.map { line -> SettingArea.entries.first { line.contains("(${it.title}") } })
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
    fun theAndroidWriterWritesVolumesAndSystemSettingsAndOpensPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val android = AndroidSettingsWriter(context)
        // Robolectric cannot grant "Modify system settings"; this only shows the call works.
        android.canWriteSystem()
        assertTrue(android.write(SettingWrite.System("screen_off_timeout", 120_000)))
        assertEquals(120_000, Settings.System.getInt(context.contentResolver, "screen_off_timeout"))
        assertTrue(android.write(SettingWrite.Volume(AudioManager.STREAM_MUSIC, 2)))
        assertEquals(2, context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(android.open("android.settings.WIFI_SETTINGS"))
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals("android.settings.WIFI_SETTINGS", started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
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
