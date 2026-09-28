package com.bizzeh.bruce.skills

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.skills.automatic.AndroidPhoneReaders
import com.bizzeh.bruce.skills.automatic.AutomaticSkills
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The automatic skills on a real phone: they read real state and nothing that identifies the phone. */
@RunWith(AndroidJUnit4::class)
class AutomaticSkillsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val registry = SkillRegistry(AutomaticSkills.create(AndroidPhoneReaders(context)))

    private fun text(tool: String, arguments: String = "{}"): String = runBlocking {
        val request = (registry.resolve(tool, arguments) as Resolution.Resolved).request
        (request.skill.execute(request.arguments) as SkillOutcome.Done).content
    }

    @Test
    fun everySkillAnswersOnThisPhone() {
        assertTrue(text("get_datetime").contains("time zone"))
        assertEquals("42", text("calculate", """{"expression":"6*7"}"""))
        assertTrue(text("get_battery_status"), Regex("Battery level: \\d+%").containsMatchIn(text("get_battery_status")))
        val device = text("get_device_info")
        assertTrue(device, device.contains(Build.MODEL) && device.contains("API ${Build.VERSION.SDK_INT}"))
        assertTrue(text("get_storage_status").startsWith("Total: "))
        assertTrue(text("get_network_status").let { it.startsWith("Online") || it.startsWith("Offline") })
    }

    @Test
    fun nothingIdentifiesThePhone() {
        val all = registry.skills.filter { it.id != "calculate" }.joinToString("\n") { text(it.id) }
        listOf(Build.FINGERPRINT, Build.ID, Build.DISPLAY, Build.HOST).filter { it.length > 4 }.forEach { assertFalse(it, all.contains(it)) }
        assertFalse(all, Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}").containsMatchIn(all))
        assertFalse(all, Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b").containsMatchIn(all))
    }
}
