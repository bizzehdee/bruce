package com.bizzeh.bruce.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzeh.bruce.policy.SkillStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [locked] is the skill's unmet requirement, if any: the skill is off whatever [state] the user chose. */
data class SkillRow(val skill: Skill, val state: SkillState, val locked: SkillRequirement? = null)

/** The Skills screen: every registered skill with the user's state for it, in registration order. */
class SkillsViewModel(
    registry: SkillRegistry,
    private val store: SkillStateStore,
    /** Requirements not met now (AppContainer.unmetRequirements). */
    unmet: Flow<Set<SkillRequirement>> = flowOf(emptySet()),
) : ViewModel() {
    /** Empty until the stored states are read, so a default never shows in place of the user's choice. */
    val rows: StateFlow<List<SkillRow>> = combine(store.stored, unmet) { stored, missing ->
        registry.skills.map { SkillRow(it, stored[it.id] ?: it.defaultState, it.requires?.takeIf { requirement -> requirement in missing }) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The store refuses Accepted for a high-risk skill unless [highRiskWarningAccepted]. */
    fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean = false) {
        viewModelScope.launch { store.set(skill, state, highRiskWarningAccepted) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
