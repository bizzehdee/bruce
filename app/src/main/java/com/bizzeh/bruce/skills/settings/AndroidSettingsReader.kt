package com.bizzeh.bruce.skills.settings

import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.location.LocationManager
import android.media.AudioManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/**
 * Reads the settings in SettingsCatalog through public Android APIs that need no runtime
 * permission. Android 12 and later refuse keys an app may not read with a SecurityException,
 * which SettingsCatalog.value turns into "cannot read".
 */
class AndroidSettingsReader(private val context: Context) : SettingsReader {
    override fun system(key: String): String? = Settings.System.getString(context.contentResolver, key)

    override fun global(key: String): String? = Settings.Global.getString(context.contentResolver, key)

    override fun volume(stream: Int): Pair<Int, Int>? {
        val audio = context.getSystemService(AudioManager::class.java) ?: return null
        return audio.getStreamVolume(stream) to audio.getStreamMaxVolume(stream)
    }

    override fun ringerMode(): Int? = context.getSystemService(AudioManager::class.java)?.ringerMode

    override fun interruptionFilter(): Int? = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter

    override fun nightMode(): Int? = context.getSystemService(UiModeManager::class.java)?.nightMode

    override fun locationEnabled(): Boolean? = context.getSystemService(LocationManager::class.java)?.isLocationEnabled

    override fun nfcEnabled(): Boolean? = NfcAdapter.getDefaultAdapter(context)?.isEnabled

    override fun powerSaveMode(): Boolean? = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode

    override fun language(): String? = context.resources.configuration.locales[0]?.getDisplayName(Locale.ENGLISH)
}
