package com.bizzeh.bruce.memory

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.R
import com.bizzeh.bruce.navigation.SubScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(private val store: MemoryStore) : ViewModel() {
    val facts: StateFlow<List<Fact>> = store.facts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun delete(fact: Fact) {
        viewModelScope.launch { store.delete(fact.id) }
    }

    fun deleteAll() {
        viewModelScope.launch { store.deleteAll() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

interface MemoryActions {
    fun delete(fact: Fact)
    fun deleteAll()
}

/** Review and delete what Bruce remembers (TASK-047). Every fact is shown, whichever mode is on. */
@Composable
fun MemoryScreen(facts: List<Fact>, actions: MemoryActions, onBack: () -> Unit) {
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    SubScreen(stringResource(R.string.memory_title), onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.memory_explained), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            if (facts.isEmpty()) {
                Text(stringResource(R.string.memory_empty), modifier = Modifier.padding(horizontal = 16.dp).testTag("memoryEmpty"))
            } else {
                TextButton(onClick = { confirmAll = true }, modifier = Modifier.padding(horizontal = 8.dp).testTag("deleteAllFacts")) {
                    Text(stringResource(R.string.memory_delete_all), color = MaterialTheme.colorScheme.error)
                }
            }
            facts.forEach { fact ->
                ListItem(
                    headlineContent = { Text(fact.text) },
                    supportingContent = {
                        Text(if (fact.scope == MemoryStore.GLOBAL) stringResource(R.string.memory_scope_global) else stringResource(R.string.memory_scope_model, fact.scope))
                    },
                    trailingContent = {
                        TextButton(onClick = { actions.delete(fact) }, modifier = Modifier.testTag("deleteFact:${fact.id}")) {
                            Text(stringResource(R.string.memory_delete))
                        }
                    },
                    modifier = Modifier.testTag("fact:${fact.id}"),
                )
            }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.memory_delete_all_confirm)) },
            text = { Text(stringResource(R.string.conversations_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; actions.deleteAll() }, modifier = Modifier.testTag("confirmDeleteAllFacts")) {
                    Text(stringResource(R.string.memory_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
