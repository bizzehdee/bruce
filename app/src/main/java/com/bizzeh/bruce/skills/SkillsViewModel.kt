package com.bizzeh.bruce.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.policy.SkillStateStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SkillRow(val skill: Skill, val state: SkillState)

/** The Skills screen: every registered skill with the user's state for it, in registration order. */
class SkillsViewModel(registry: SkillRegistry, private val store: SkillStateStore) : ViewModel() {
    /** Empty until the stored states are read, so a default never shows in place of the user's choice. */
    val rows: StateFlow<List<SkillRow>> = store.stored
        .map { stored -> registry.skills.map { SkillRow(it, stored[it.id] ?: it.defaultState) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The store refuses Accepted for a high-risk skill unless [highRiskWarningAccepted]. */
    fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean = false) {
        viewModelScope.launch { store.set(skill, state, highRiskWarningAccepted) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
