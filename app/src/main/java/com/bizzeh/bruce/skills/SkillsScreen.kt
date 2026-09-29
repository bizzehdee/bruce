package com.bizzeh.bruce.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizzeh.bruce.R
import com.bizzeh.bruce.navigation.SubScreen

interface SkillsActions {
    fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean)
    fun openPermissions()
    fun openNetworkSettings() = Unit
}

@Composable
fun SkillsScreen(rows: List<SkillRow>, actions: SkillsActions, onBack: () -> Unit) {
    // The id of a high-risk skill waiting on the warning; saved so rotation keeps the dialog.
    var warning by rememberSaveable { mutableStateOf<String?>(null) }
    SubScreen(stringResource(R.string.settings_skills), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("skills")) {
            Text(
                stringResource(R.string.skills_states_explained),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            rows.forEach { row ->
                HorizontalDivider()
                SkillItem(row, actions) { state ->
                    if (row.skill.highRisk && state == SkillState.ACCEPTED) warning = row.skill.id else actions.set(row.skill, state, false)
                }
            }
        }
    }
    rows.firstOrNull { it.skill.id == warning }?.let { row ->
        AlertDialog(
            onDismissRequest = { warning = null },
            title = { Text(stringResource(R.string.skills_warning_title, SkillText.name(row.skill))) },
            text = { Text(stringResource(R.string.skills_warning_text)) },
            confirmButton = {
                TextButton(onClick = { actions.set(row.skill, SkillState.ACCEPTED, true); warning = null }, modifier = Modifier.testTag("acceptRisk")) {
                    Text(stringResource(R.string.skills_warning_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { warning = null }, modifier = Modifier.testTag("cancelRisk")) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillItem(row: SkillRow, actions: SkillsActions, onSelect: (SkillState) -> Unit) {
    val skill = row.skill
    Column(modifier = Modifier.padding(16.dp).testTag("skill:${skill.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(SkillText.name(skill), style = MaterialTheme.typography.titleMedium)
        Text(SkillText.summary(skill), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (skill.highRisk) {
            Text(stringResource(R.string.skills_high_risk), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("highRisk:${skill.id}"))
        }
        when (row.locked) {
            SkillRequirement.FILE_GRANT -> Locked(R.string.skills_locked_files, R.string.settings_permissions, "permissions:${skill.id}", actions::openPermissions)
            SkillRequirement.NETWORK_ALLOWED -> Locked(R.string.skills_locked_network, R.string.settings_network, "network:${skill.id}", actions::openNetworkSettings)
            null -> Unit
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            SkillState.entries.forEachIndexed { index, state ->
                SegmentedButton(
                    // A locked skill is off, and cannot be changed until its requirement is met; the user's choice returns then.
                    selected = if (row.locked != null) state == SkillState.DECLINED else state == row.state,
                    enabled = row.locked == null,
                    onClick = { if (state != row.state) onSelect(state) },
                    shape = SegmentedButtonDefaults.itemShape(index, SkillState.entries.size),
                    modifier = Modifier.testTag("state:${skill.id}:$state"),
                ) { Text(stringResource(SkillText.state(state))) }
            }
        }
    }
}

@Composable
private fun Locked(reason: Int, link: Int, tag: String, open: () -> Unit) {
    Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("locked:${tag.substringAfter(':')}"))
    TextButton(onClick = open, modifier = Modifier.testTag(tag)) { Text(stringResource(link)) }
}
