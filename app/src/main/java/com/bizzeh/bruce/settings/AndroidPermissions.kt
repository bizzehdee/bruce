package com.bizzeh.bruce.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** An Android permission Bruce's manifest asks for, as this phone has it now. */
data class HeldPermission(val name: String, val systemLabel: String, val granted: Boolean, val kind: PermissionKind)

enum class PermissionKind {
    /** The user turns it on or off in Android's settings. */
    USER_CONTROLLED,

    /** Granted at install; Android has no way to revoke it. */
    INSTALL_TIME,
}

object AndroidPermissions {
    fun read(context: Context): List<HeldPermission> {
        val packageManager = context.packageManager
        val info = packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val names = info.requestedPermissions.orEmpty()
        return names.mapIndexedNotNull { index, name ->
            if (name == Manifest.permission.POST_NOTIFICATIONS) {
                // Before Android 13 this is not a permission at all, but notifications can still be switched off.
                return@mapIndexedNotNull HeldPermission(name, name, NotificationManagerCompat.from(context).areNotificationsEnabled(), PermissionKind.USER_CONTROLLED)
            }
            val permission = try {
                packageManager.getPermissionInfo(name, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return@mapIndexedNotNull null
            }
            // Permissions a library defines in Bruce's own package guard Bruce's components; they grant nothing on the phone.
            if (permission.packageName == context.packageName) return@mapIndexedNotNull null
            val kind = if (permission.protection == PermissionInfo.PROTECTION_DANGEROUS) PermissionKind.USER_CONTROLLED else PermissionKind.INSTALL_TIME
            HeldPermission(name, permission.loadLabel(packageManager).toString(), granted(info, index), kind)
        }
    }

    /** Where the user changes [permission]: the notification page for notifications, Bruce's app page otherwise. */
    fun settingsIntent(context: Context, permission: HeldPermission): Intent =
        if (permission.name == Manifest.permission.POST_NOTIFICATIONS) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }

    private fun granted(info: PackageInfo, index: Int): Boolean =
        (info.requestedPermissionsFlags?.getOrNull(index) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
}
