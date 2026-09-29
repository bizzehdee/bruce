package com.bizzeh.bruce.skills.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

/**
 * Writes the few settings Android lets an app write: `Settings.System` values after the user grants
 * "Modify system settings", and volumes through AudioManager. Pages open as a new task, as a skill
 * runs outside any activity.
 */
class AndroidSettingsWriter(private val context: Context) : SettingsWriter {
    override fun canWriteSystem(): Boolean = Settings.System.canWrite(context)

    override fun write(write: SettingWrite): Boolean = try {
        when (write) {
            is SettingWrite.System -> Settings.System.putInt(context.contentResolver, write.key, write.value)
            is SettingWrite.Volume -> {
                context.getSystemService(AudioManager::class.java).setStreamVolume(write.stream, write.level, 0)
                true
            }
        }
    } catch (e: SecurityException) {
        // Android refuses, for example, a volume change that would leave Do Not Disturb.
        false
    }

    override fun open(page: String): Boolean = try {
        context.startActivity(Intent(page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
