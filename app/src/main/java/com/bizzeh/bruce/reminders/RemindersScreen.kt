package com.bizzeh.bruce.reminders

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.R
import com.bizzeh.bruce.navigation.SubScreen
import com.bizzeh.bruce.settings.SettingsText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(private val store: ReminderStore, private val tasks: ScheduledTasks) : ViewModel() {
    val reminders: StateFlow<List<Reminder>> = store.upcoming.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    val scheduled: StateFlow<List<ScheduledTask>> = tasks.upcoming.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun cancel(reminder: Reminder) {
        viewModelScope.launch { store.cancel(reminder.id) }
    }

    fun cancel(task: ScheduledTask) {
        viewModelScope.launch { tasks.cancel(task.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

interface RemindersActions {
    fun cancel(reminder: Reminder)
    fun cancelTask(task: ScheduledTask) = Unit
    fun allowExact()
}

/** Reminders waiting to be shown, each with Cancel (TASK-094); says so when they may come late. */
@Composable
fun RemindersScreen(reminders: List<Reminder>, exactAllowed: Boolean, actions: RemindersActions, tasks: List<ScheduledTask> = emptyList(), onBack: () -> Unit) {
    SubScreen(stringResource(R.string.reminders_title), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("reminders")) {
            if (!exactAllowed) {
                Text(stringResource(R.string.reminders_inexact), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                TextButton(onClick = actions::allowExact, modifier = Modifier.padding(horizontal = 8.dp).testTag("allowExact")) { Text(stringResource(R.string.reminders_allow_exact)) }
            }
            if (reminders.isEmpty() && tasks.isEmpty()) {
                Text(stringResource(R.string.reminders_empty), modifier = Modifier.padding(16.dp).testTag("remindersEmpty"))
            }
            reminders.forEach { reminder ->
                ListItem(
                    headlineContent = { Text(reminder.text) },
                    supportingContent = { Text(SettingsText.date(reminder.atMillis)) },
                    trailingContent = {
                        TextButton(onClick = { actions.cancel(reminder) }, modifier = Modifier.testTag("cancelReminder:${reminder.id}")) {
                            Text(stringResource(R.string.reminders_cancel))
                        }
                    },
                    modifier = Modifier.testTag("reminder:${reminder.id}"),
                )
            }
            if (tasks.isNotEmpty()) {
                Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }
            tasks.forEach { task ->
                val repeat = when (task.repeat) {
                    Repeat.ONCE -> ""
                    Repeat.DAILY -> " · " + stringResource(R.string.tasks_repeat_daily)
                    Repeat.WEEKLY -> " · " + stringResource(R.string.tasks_repeat_weekly)
                }
                ListItem(
                    headlineContent = { Text(task.prompt) },
                    supportingContent = { Text(SettingsText.date(task.nextAt) + repeat) },
                    trailingContent = {
                        TextButton(onClick = { actions.cancelTask(task) }, modifier = Modifier.testTag("cancelTask:${task.id}")) {
                            Text(stringResource(R.string.reminders_cancel))
                        }
                    },
                    modifier = Modifier.testTag("task:${task.id}"),
                )
            }
        }
    }
}
