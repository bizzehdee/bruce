package com.bizzeh.bruce.skills

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.ui.theme.BruceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = "w400dp-h1600dp")
@RunWith(RobolectricTestRunner::class)
class SkillsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private val store = SkillStateStore(database.policy())

    private fun skill(id: String, default: SkillState, highRisk: Boolean = false, scope: ResourceScope = ResourceScope.NONE) =
        Skill(id, 1, "Model-facing description of $id.", InputSchema(), emptySet(), default, highRisk, scope) { SkillOutcome.Done("") }

    private val clock = skill("get_datetime", SkillState.ACCEPTED)
    private val delete = skill("delete_file", SkillState.ASK, highRisk = true, scope = ResourceScope.GRANTED_FILES)
    private val registry = SkillRegistry(listOf(clock, delete))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun SkillsViewModel.awaitRows(predicate: (List<SkillRow>) -> Boolean) = runBlocking {
        withTimeout(5_000) { rows.first { it.isNotEmpty() && predicate(it) } }
    }

    @Test
    fun rowsShowDefaultsUntilTheUserChoosesAndThenTheirChoice() {
        val viewModel = SkillsViewModel(registry, store)

        val defaults = viewModel.awaitRows { true }
        assertEquals(listOf(clock to SkillState.ACCEPTED, delete to SkillState.ASK), defaults.map { it.skill to it.state })

        viewModel.set(clock, SkillState.DECLINED)
        viewModel.awaitRows { rows -> rows.first().state == SkillState.DECLINED }
        viewModel.set(delete, SkillState.ACCEPTED, highRiskWarningAccepted = true)
        viewModel.awaitRows { rows -> rows.last().state == SkillState.ACCEPTED }
    }

    @Test
    fun highRiskSkillIsNotAcceptedWithoutTheWarning() {
        val viewModel = SkillsViewModel(registry, store)
        viewModel.awaitRows { true }

        viewModel.set(delete, SkillState.ACCEPTED)
        viewModel.set(clock, SkillState.ASK)

        val rows = viewModel.awaitRows { it.first().state == SkillState.ASK }
        assertEquals(SkillState.ASK, rows.last().state)
    }

    private val calls = mutableListOf<String>()
    private val actions = object : SkillsActions {
        override fun set(skill: Skill, state: SkillState, highRiskWarningAccepted: Boolean) { calls += "${skill.id} $state $highRiskWarningAccepted" }
        override fun openPermissions() { calls += "permissions" }
    }

    private fun show() = compose.setContent {
        BruceTheme { SkillsScreen(listOf(SkillRow(clock, SkillState.ACCEPTED), SkillRow(delete, SkillState.ASK)), actions) {} }
    }

    @Test
    fun screenShowsEachSkillWithItsStateAndChangesIt() {
        show()

        compose.onNodeWithText("Date and time").assertExists()
        compose.onNodeWithText("Reads the phone's date, time and time zone.").assertExists()
        compose.onNodeWithText("Model-facing description of delete_file.").assertExists()
        compose.onNodeWithTag("state:get_datetime:ACCEPTED").assertIsSelected()
        compose.onNodeWithTag("highRisk:delete_file").assertExists()
        compose.onNodeWithTag("highRisk:get_datetime").assertDoesNotExist()
        compose.onNodeWithTag("permissions:get_datetime").assertDoesNotExist()

        compose.onNodeWithTag("state:get_datetime:ACCEPTED").performClick()
        compose.onNodeWithTag("state:get_datetime:DECLINED").performClick()
        compose.onNodeWithTag("state:delete_file:DECLINED").performScrollTo().performClick()
        compose.onNodeWithTag("permissions:delete_file").performScrollTo().performClick()

        assertEquals(listOf("get_datetime DECLINED false", "delete_file DECLINED false", "permissions"), calls)
    }

    @Test
    fun acceptingAHighRiskSkillNeedsTheWarningAccepted() {
        show()

        compose.onNodeWithTag("state:delete_file:ACCEPTED").performScrollTo().performClick()
        compose.onNodeWithTag("cancelRisk").performClick()
        compose.onNodeWithTag("cancelRisk").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithTag("state:delete_file:ACCEPTED").performClick()
        compose.onNodeWithText("Always allow delete_file?").assertExists()
        compose.onNodeWithTag("acceptRisk").performClick()
        assertEquals(listOf("delete_file ACCEPTED true"), calls)
    }
}
