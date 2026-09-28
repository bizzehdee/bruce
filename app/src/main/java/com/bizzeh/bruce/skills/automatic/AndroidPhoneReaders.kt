package com.bizzeh.bruce.skills.automatic

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import java.time.ZonedDateTime

/**
 * The phone's state for the automatic skills, from Android APIs that need no runtime permission
 * (network status needs ACCESS_NETWORK_STATE, granted at install). Nothing that identifies the
 * phone or its networks is read.
 */
class AndroidPhoneReaders(private val context: Context) : PhoneReaders {
    override fun now(): ZonedDateTime = ZonedDateTime.now()

    override fun battery(): BatteryStatus {
        // A sticky broadcast: registering with no receiver returns the latest battery state.
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryStatus(null, null, null)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return BatteryStatus(
            percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            },
            // Tenths of a degree Celsius.
            temperatureCelsius = if (temperature == Int.MIN_VALUE) null else temperature / 10.0,
        )
    }

    override fun device(): DeviceInfo {
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val processor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.takeUnless { it == Build.UNKNOWN } else null
        return DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, processor ?: Build.HARDWARE, memory.totalMem)
    }

    override fun storage(): StorageStatus {
        val stat = StatFs(context.filesDir.absolutePath)
        return StorageStatus(stat.totalBytes, stat.availableBytes)
    }

    override fun network(): NetworkStatus {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return NetworkStatus(online = false, transport = null)
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.OTHER
        }
        return NetworkStatus(online, transport)
    }
}
