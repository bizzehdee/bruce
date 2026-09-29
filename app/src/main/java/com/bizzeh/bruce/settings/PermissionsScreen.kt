package com.bizzeh.bruce.settings

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.bizzeh.bruce.policy.FolderInstructionsStore
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.policy.InstructionsChoice
import java.text.DateFormat
import java.util.Date

interface GrantActions {
    fun addFolder()
    fun addFile()
    fun revoke(grant: Grant)
    fun review(grant: Grant) = Unit
    fun choose(review: FolderReview, follow: Boolean) = Unit
    fun closeReview() = Unit
}

/** Reads the permissions again whenever the screen resumes, so a change made in Android's settings shows on return. */
@Composable
fun PermissionsScreen(
    grants: List<Grant>,
    addFailed: Boolean,
    grantActions: GrantActions,
    onOpenNetworkSettings: () -> Unit,
    onBack: () -> Unit,
    folders: Map<Long, FolderReview> = emptyMap(),
    reviewing: FolderReview? = null,
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(AndroidPermissions.read(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = AndroidPermissions.read(context)
        onPauseOrDispose { }
    }
    PermissionsContent(
        grants = grants,
        addFailed = addFailed,
        grantActions = grantActions,
        permissions = permissions,
        onChange = { context.startActivity(AndroidPermissions.settingsIntent(context, it)) },
        onOpenNetworkSettings = onOpenNetworkSettings,
        onBack = onBack,
        folders = folders,
        reviewing = reviewing,
    )
}

@Composable
fun PermissionsContent(
    grants: List<Grant>,
    addFailed: Boolean,
    grantActions: GrantActions,
    permissions: List<HeldPermission>,
    onChange: (HeldPermission) -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onBack: () -> Unit,
    folders: Map<Long, FolderReview> = emptyMap(),
    reviewing: FolderReview? = null,
) {
    reviewing?.let { ReviewDialog(it, grantActions) }
    SubScreen(stringResource(R.string.settings_permissions), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Heading(R.string.permissions_files)
            if (grants.isEmpty()) {
                Text(
                    stringResource(R.string.permissions_files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("grantsEmpty"),
                )
            }
            grants.forEach { GrantRow(it, folders[it.id], grantActions) }
            if (addFailed) {
                Text(
                    stringResource(R.string.permissions_add_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("addFailed"),
                )
            }
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = grantActions::addFolder, modifier = Modifier.testTag("addFolder")) { Text(stringResource(R.string.permissions_add_folder)) }
                TextButton(onClick = grantActions::addFile, modifier = Modifier.testTag("addFile")) { Text(stringResource(R.string.permissions_add_file)) }
            }
            Heading(R.string.permissions_android)
            permissions.forEach { PermissionRow(it, onChange, onOpenNetworkSettings) }
        }
    }
}

@Composable
private fun GrantRow(grant: Grant, folder: FolderReview?, actions: GrantActions) {
    val kind = stringResource(if (grant.kind == GrantKind.FOLDER) R.string.permissions_kind_folder else R.string.permissions_kind_file)
    val date = remember(grant.grantedAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(grant.grantedAt)) }
    ListItem(
        headlineContent = { Text(grant.name) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.permissions_granted_on, kind, date))
                if (!grant.available) {
                    Text(stringResource(R.string.permissions_grant_lost), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lost:${grant.id}"))
                }
                folder?.let { InstructionsStatus(grant, it, actions) }
            }
        },
        trailingContent = {
            TextButton(onClick = { actions.revoke(grant) }, modifier = Modifier.testTag("revoke:${grant.id}")) { Text(stringResource(R.string.permissions_revoke)) }
        },
        modifier = Modifier.testTag("grant:${grant.id}"),
    )
}

@Composable
private fun InstructionsStatus(grant: Grant, folder: FolderReview, actions: GrantActions) {
    val status = when {
        folder.instructions.problem != null -> R.string.instructions_unusable
        folder.choice == InstructionsChoice.FOLLOW -> R.string.instructions_followed
        folder.choice == InstructionsChoice.IGNORE -> R.string.instructions_ignored
        else -> R.string.instructions_undecided
    }
    val colour = if (folder.choice == InstructionsChoice.UNDECIDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(stringResource(status), color = colour, modifier = Modifier.testTag("instructions:${grant.id}"))
    TextButton(onClick = { actions.review(grant) }, modifier = Modifier.testTag("review:${grant.id}")) { Text(stringResource(R.string.instructions_review)) }
}

/** Shows what the folder asks, in full, before the user lets Bruce follow it. */
@Composable
private fun ReviewDialog(folder: FolderReview, actions: GrantActions) {
    val instructions = folder.instructions
    AlertDialog(
        onDismissRequest = actions::closeReview,
        title = { Text(stringResource(R.string.instructions_title, instructions.folder)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).testTag("instructionsText")) {
                Text(stringResource(R.string.instructions_explained))
                instructions.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                instructions.agentsMd?.let {
                    Text(FolderInstructionsStore.AGENTS_MD, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (instructions.agentsFiles.isNotEmpty()) {
                    Text(stringResource(R.string.instructions_more_files), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    instructions.agentsFiles.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (instructions.problem == null) {
                TextButton(onClick = { actions.choose(folder, follow = true) }, modifier = Modifier.testTag("follow")) { Text(stringResource(R.string.instructions_follow)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.choose(folder, follow = false) }, modifier = Modifier.testTag("ignore")) { Text(stringResource(R.string.instructions_ignore)) }
        },
    )
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
