package com.bizzeh.bruce.skills.automatic

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.Resolution
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.time.ZoneId
import java.time.ZonedDateTime

/** Robolectric for Android's org.json (argument validation) and the Android readers. */
@RunWith(RobolectricTestRunner::class)
class AutomaticSkillsTest {
    private val phone = object : PhoneReaders {
        override fun now() = ZonedDateTime.of(2026, 9, 28, 14, 37, 5, 0, ZoneId.of("Europe/London"))
        override fun battery() = BatteryStatus(81, true, 31.4)
        override fun device() = DeviceInfo("Google", "Pixel 11", "17", 37, "Tensor G6", 16L shl 30)
        override fun storage() = StorageStatus(256L shl 30, 100L shl 30)
        override fun network() = NetworkStatus(true, Transport.WIFI)
    }
    private val registry = SkillRegistry(AutomaticSkills.create(phone))

    private fun run(tool: String, arguments: String = "{}"): SkillOutcome = runBlocking {
        val request = (registry.resolve(tool, arguments) as Resolution.Resolved).request
        request.skill.execute(request.arguments)
    }

    private fun text(tool: String, arguments: String = "{}") = (run(tool, arguments) as SkillOutcome.Done).content

    @Test
    fun allSixAreAcceptedAndNeedNoRuntimePermission() {
        assertEquals(
            listOf("get_datetime", "calculate", "get_battery_status", "get_device_info", "get_storage_status", "get_network_status"),
            registry.skills.map { it.id },
        )
        assertTrue(registry.skills.all { it.defaultState == SkillState.ACCEPTED && it.androidPermissions.isEmpty() && !it.highRisk })
    }

    @Test
    fun eachSkillReportsWhatItReads() {
        assertEquals("Monday 28 September 2026, 14:37:05, time zone Europe/London (UTC+01:00)", text("get_datetime"))
        assertEquals("7006652", text("calculate", """{"expression":"1234 * 5678"}"""))
        assertEquals("Battery level: 81%\nCharging\nTemperature: 31.4 °C", text("get_battery_status"))
        assertEquals("Manufacturer: Google\nModel: Pixel 11\nAndroid: 17 (API 37)\nProcessor: Tensor G6\nMemory: 16.00 GB", text("get_device_info"))
        assertEquals("Total: 256.00 GB\nUsed: 156.00 GB\nFree: 100.00 GB", text("get_storage_status"))
        assertEquals("Online, using Wi-Fi.", text("get_network_status"))
    }

    @Test
    fun unknownsAndOtherStatesAreSaidPlainly() {
        assertEquals("Battery level: unknown", AutomaticSkills.battery(BatteryStatus(null, null, null)))
        assertEquals("Battery level: 5%\nNot charging", AutomaticSkills.battery(BatteryStatus(5, false, null)))
        assertEquals("Offline: no working internet connection.", AutomaticSkills.network(NetworkStatus(false, Transport.WIFI)))
        assertEquals("Online, using mobile data.", AutomaticSkills.network(NetworkStatus(true, Transport.MOBILE)))
        assertEquals("Online, using Ethernet.", AutomaticSkills.network(NetworkStatus(true, Transport.ETHERNET)))
        assertEquals("Online, using another kind of connection.", AutomaticSkills.network(NetworkStatus(true, null)))
        assertEquals("Manufacturer: x\nModel: y\nAndroid: 10 (API 29)\nMemory: 1.00 GB", AutomaticSkills.device(DeviceInfo("x", "y", "10", 29, null, 1L shl 30)))
        assertEquals("UTC", AutomaticSkills.dateTime(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))).substringAfter("time zone ").substringBefore(" "))
        assertTrue(AutomaticSkills.dateTime(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))).endsWith("(UTC+00:00)"))
    }

    @Test
    fun aCalculationItCannotDoIsARetryableFailure() {
        val failed = run("calculate", """{"expression":"1 / 0"}""") as SkillOutcome.Failed
        assertEquals(DenialCode.TOOL_FAILED, failed.code)
        assertEquals("Cannot calculate that: division by zero.", failed.message)
        assertTrue(failed.retryable)
    }

    @Test
    fun androidReadersReadBatteryStorageDeviceAndNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val readers = AndroidPhoneReaders(context)
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 45).putExtra(BatteryManager.EXTRA_SCALE, 50)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
                .putExtra(BatteryManager.EXTRA_TEMPERATURE, 287),
        )
        assertEquals(BatteryStatus(90, false, 28.7), readers.battery())
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL))
        assertEquals(BatteryStatus(null, true, null), readers.battery())
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN))
        assertNull(readers.battery().charging)

        assertTrue(readers.storage().totalBytes >= readers.storage().freeBytes)
        assertTrue(readers.device().model.isNotEmpty())
        assertTrue(readers.now().year >= 2026)

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork!!
        fun network(vararg transports: Int, validated: Boolean = true): NetworkStatus {
            val capabilities = ShadowNetworkCapabilities.newInstance()
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            transports.forEach { shadowOf(capabilities).addTransportType(it) }
            shadowOf(connectivity).setNetworkCapabilities(network, capabilities)
            return readers.network()
        }
        assertEquals(NetworkStatus(true, Transport.WIFI), network(NetworkCapabilities.TRANSPORT_WIFI))
        assertEquals(NetworkStatus(true, Transport.MOBILE), network(NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals(NetworkStatus(true, Transport.ETHERNET), network(NetworkCapabilities.TRANSPORT_ETHERNET))
        assertEquals(NetworkStatus(false, Transport.OTHER), network(NetworkCapabilities.TRANSPORT_BLUETOOTH, validated = false))
        shadowOf(connectivity).setDefaultNetworkActive(false)
        shadowOf(connectivity).setActiveNetworkInfo(null)
    }
}
