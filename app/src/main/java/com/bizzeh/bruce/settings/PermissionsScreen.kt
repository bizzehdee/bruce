package com.bizzeh.bruce.settings

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bizzeh.bruce.R
import com.bizzeh.bruce.navigation.SubScreen

/** Reads the permissions again whenever the screen resumes, so a change made in Android's settings shows on return. */
@Composable
fun PermissionsScreen(onOpenNetworkSettings: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(AndroidPermissions.read(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = AndroidPermissions.read(context)
        onPauseOrDispose { }
    }
    PermissionsContent(
        permissions = permissions,
        onChange = { context.startActivity(AndroidPermissions.settingsIntent(context, it)) },
        onOpenNetworkSettings = onOpenNetworkSettings,
        onBack = onBack,
    )
}

@Composable
fun PermissionsContent(
    permissions: List<HeldPermission>,
    onChange: (HeldPermission) -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onBack: () -> Unit,
) {
    SubScreen(stringResource(R.string.settings_permissions), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Heading(R.string.permissions_files)
            Text(
                stringResource(R.string.permissions_files_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("grantsEmpty"),
            )
            Heading(R.string.permissions_android)
            permissions.forEach { PermissionRow(it, onChange, onOpenNetworkSettings) }
        }
    }
}

@Composable
private fun PermissionRow(permission: HeldPermission, onChange: (HeldPermission) -> Unit, onOpenNetworkSettings: () -> Unit) {
    val text = PERMISSION_TEXT[permission.name]
    val state = when {
        permission.kind == PermissionKind.INSTALL_TIME -> R.string.permissions_install_time
        permission.granted -> R.string.permissions_allowed
        else -> R.string.permissions_not_allowed
    }
    ListItem(
        headlineContent = { Text(text?.let { stringResource(it.first) } ?: permission.systemLabel) },
        supportingContent = {
            Column {
                text?.let { Text(stringResource(it.second)) }
                Text(stringResource(state), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("state:${permission.name}"))
                when {
                    permission.kind == PermissionKind.USER_CONTROLLED -> TextButton(onClick = { onChange(permission) }, modifier = Modifier.testTag("change:${permission.name}")) {
                        Text(stringResource(R.string.permissions_change))
                    }
                    permission.name == Manifest.permission.INTERNET -> TextButton(onClick = onOpenNetworkSettings, modifier = Modifier.testTag("networkMode")) {
                        Text(stringResource(R.string.permissions_network_mode))
                    }
                }
            }
        },
        modifier = Modifier.testTag("permission:${permission.name}"),
    )
}

/** Android 17 adds it to apps that hold INTERNET and target API 36 or lower; Bruce's manifest never asks for it. */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Bruce's own name and reason for each permission it asks for; others show Android's label. */
private val PERMISSION_TEXT = mapOf(
    Manifest.permission.POST_NOTIFICATIONS to (R.string.permission_notifications to R.string.permission_notifications_summary),
    Manifest.permission.INTERNET to (R.string.permission_internet to R.string.permission_internet_summary),
    LOCAL_NETWORK to (R.string.permission_local_network to R.string.permission_local_network_summary),
    Manifest.permission.ACCESS_NETWORK_STATE to (R.string.permission_network_state to R.string.permission_network_state_summary),
)
