package com.bizzeh.bruce.settings

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.ui.theme.BruceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(qualifiers = "w400dp-h1600dp")
@RunWith(RobolectricTestRunner::class)
class PermissionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val noGrantActions = object : GrantActions {
        override fun addFolder() = Unit
        override fun addFile() = Unit
        override fun revoke(grant: Grant) = Unit
    }

    private fun definePermission(name: String, protection: Int, packageName: String = "android") {
        shadowOf(context.packageManager).addPermissionInfo(
            PermissionInfo().apply {
                this.name = name
                this.packageName = packageName
                @Suppress("DEPRECATION")
                protectionLevel = protection
            },
        )
    }

    /** Replaces what Bruce's package asks for, with each permission's grant flag. */
    private fun request(vararg permissions: Pair<String, Boolean>) {
        val packageManager = shadowOf(context.packageManager)
        val info = packageManager.getInternalMutablePackageInfo(context.packageName)
        info.requestedPermissions = permissions.map { it.first }.toTypedArray()
        info.requestedPermissionsFlags = permissions.map { if (it.second) PackageInfo.REQUESTED_PERMISSION_GRANTED else 0 }.toIntArray()
    }

    @Test
    fun readsWhatThisPhoneGrantedAndWhichTheUserControls() {
        definePermission(Manifest.permission.INTERNET, PermissionInfo.PROTECTION_NORMAL)
        definePermission(Manifest.permission.CAMERA, PermissionInfo.PROTECTION_DANGEROUS)
        definePermission("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", PermissionInfo.PROTECTION_SIGNATURE, context.packageName)
        request(
            Manifest.permission.INTERNET to true,
            Manifest.permission.CAMERA to false,
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to true,
            "com.example.UNKNOWN" to true,
            Manifest.permission.POST_NOTIFICATIONS to false,
        )

        val permissions = AndroidPermissions.read(context).associateBy { it.name }

        assertEquals(setOf(Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS), permissions.keys)
        assertEquals(PermissionKind.INSTALL_TIME, permissions.getValue(Manifest.permission.INTERNET).kind)
        assertTrue(permissions.getValue(Manifest.permission.INTERNET).granted)
        assertEquals(PermissionKind.USER_CONTROLLED, permissions.getValue(Manifest.permission.CAMERA).kind)
        assertFalse(permissions.getValue(Manifest.permission.CAMERA).granted)
        assertEquals(PermissionKind.USER_CONTROLLED, permissions.getValue(Manifest.permission.POST_NOTIFICATIONS).kind)
    }

    @Test
    fun notificationsFollowWhetherAndroidLetsBruceNotify() {
        request(Manifest.permission.POST_NOTIFICATIONS to false)
        val notifications = shadowOf(context.getSystemService(android.app.NotificationManager::class.java))

        notifications.setNotificationsEnabled(true)
        assertTrue(AndroidPermissions.read(context).single().granted)
        notifications.setNotificationsEnabled(false)
        assertFalse(AndroidPermissions.read(context).single().granted)
    }

    @Test
    fun changesOpenAndroidSettingsForBruce() {
        val notifications = AndroidPermissions.settingsIntent(context, HeldPermission(Manifest.permission.POST_NOTIFICATIONS, "", true, PermissionKind.USER_CONTROLLED))
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals(context.packageName, notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        val camera = AndroidPermissions.settingsIntent(context, HeldPermission(Manifest.permission.CAMERA, "", true, PermissionKind.USER_CONTROLLED))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, camera.action)
        assertEquals("package:${context.packageName}", camera.data.toString())
    }

    @Test
    fun listsGrantsAndPermissionsWithTheirActions() {
        val calls = mutableListOf<String>()
        val notifications = HeldPermission(Manifest.permission.POST_NOTIFICATIONS, "", false, PermissionKind.USER_CONTROLLED)
        val permissions = listOf(
            notifications,
            HeldPermission(Manifest.permission.INTERNET, "", true, PermissionKind.INSTALL_TIME),
            HeldPermission("com.example.OTHER", "Other thing", true, PermissionKind.USER_CONTROLLED),
        )
        compose.setContent {
            BruceTheme { PermissionsContent(emptyList(), false, noGrantActions, permissions, { calls += it.name }, { calls += "network" }, {}) }
        }

        compose.onNodeWithText("No files or folders granted yet.").assertIsDisplayed()
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithTag("state:${Manifest.permission.POST_NOTIFICATIONS}", useUnmergedTree = true).assertTextEquals("Not allowed")
        compose.onNodeWithTag("state:${Manifest.permission.INTERNET}", useUnmergedTree = true).assertTextEquals("Always granted by Android. It cannot be turned off.")
        compose.onNodeWithTag("state:com.example.OTHER", useUnmergedTree = true).assertTextEquals("Allowed")
        compose.onNodeWithText("Other thing").assertIsDisplayed()
        compose.onNodeWithTag("change:${Manifest.permission.INTERNET}").assertDoesNotExist()

        compose.onNodeWithTag("change:${Manifest.permission.POST_NOTIFICATIONS}").performClick()
        compose.onNodeWithTag("networkMode").performClick()
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS, "network"), calls)
    }

    @Test
    fun grantsAreListedWithTheirStateAndCanBeRevokedOrAdded() {
        val calls = mutableListOf<String>()
        val actions = object : GrantActions {
            override fun addFolder() { calls += "folder" }
            override fun addFile() { calls += "file" }
            override fun revoke(grant: Grant) { calls += "revoke ${grant.name}" }
        }
        val grants = listOf(
            Grant(1, android.net.Uri.parse("content://docs/tree/a"), "Documents", com.bizzeh.bruce.policy.GrantKind.FOLDER, 0, available = true),
            Grant(2, android.net.Uri.parse("content://docs/b"), "report.pdf", com.bizzeh.bruce.policy.GrantKind.FILE, 0, available = false),
        )
        compose.setContent { BruceTheme { PermissionsContent(grants, true, actions, emptyList(), {}, {}, {}) } }

        compose.onNodeWithTag("grantsEmpty").assertDoesNotExist()
        compose.onNodeWithText("Documents").assertIsDisplayed()
        compose.onNodeWithTag("lost:1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("lost:2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("addFailed").assertIsDisplayed()

        compose.onNodeWithTag("revoke:2").performClick()
        compose.onNodeWithTag("addFolder").performClick()
        compose.onNodeWithTag("addFile").performClick()
        assertEquals(listOf("revoke report.pdf", "folder", "file"), calls)
    }

    @Test
    fun screenOpensAndroidSettingsWhenAskedToChange() {
        request(Manifest.permission.POST_NOTIFICATIONS to false)
        compose.setContent { BruceTheme { PermissionsScreen(emptyList(), false, noGrantActions, {}, {}) } }

        compose.onNodeWithTag("change:${Manifest.permission.POST_NOTIFICATIONS}").performClick()

        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
    }
}
