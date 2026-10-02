package com.bizzeh.bruce.setup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.settings.NetworkSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import com.bizzeh.bruce.R
import com.bizzeh.bruce.settings.InferenceSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import com.bizzeh.bruce.models.DeviceTier
import com.bizzeh.bruce.models.SuggestedModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** DataStore reads and writes on its own IO thread, so these tests wait on state rather than on the test scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @TempDir
    lateinit var dir: File

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private inner class Fixture(scope: TestScope, askNotifications: Boolean = false, suggestions: List<SuggestedModel> = emptyList(), hasModel: Boolean = false) {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(dir, "s.preferences_pb") }
        val setup = SetupSettingsRepository(dataStore)
        val network = NetworkSettingsRepository(dataStore)
        val inference = InferenceSettingsRepository(dataStore)
        val model = MutableStateFlow(hasModel)
        val viewModel = SetupViewModel(setup, network, inference, askNotifications, suggestions, model)

        suspend fun awaitStep(step: SetupStep) = viewModel.state.first { it.step == step }
    }

    @Test
    fun notificationsStepOnlyWhenThePermissionIsNeeded() {
        assertEquals(listOf(SetupStep.WELCOME, SetupStep.NETWORK, SetupStep.NOTIFICATIONS, SetupStep.MODEL), setupSteps(askNotifications = true))
        assertEquals(listOf(SetupStep.WELCOME, SetupStep.NETWORK, SetupStep.MODEL), setupSteps(askNotifications = false))
    }

    @Test
    fun walksEveryStepInOrderWithFirstAndLastMarked() = runTest(dispatcher) {
        val f = Fixture(this, askNotifications = true)
        f.viewModel.back()
        assertEquals(SetupState(SetupStep.WELCOME, isFirst = true, isLast = false), f.awaitStep(SetupStep.WELCOME))

        f.viewModel.next()
        f.awaitStep(SetupStep.NETWORK)
        f.viewModel.next()
        f.awaitStep(SetupStep.NOTIFICATIONS)
        repeat(3) { f.viewModel.next() }
        assertEquals(SetupState(SetupStep.MODEL, isFirst = false, isLast = true), f.awaitStep(SetupStep.MODEL))

        f.viewModel.back()
        assertFalse(f.awaitStep(SetupStep.NOTIFICATIONS).isLast)
    }

    @Test
    fun theModelStepCarriesThePhonesSuggestions() = runTest(dispatcher) {
        val suggestion = SuggestedModel("a/repo", "m.gguf", 1, "M", R.string.suggested_qwen3_1_7b, setOf(DeviceTier.EIGHT_GB))
        val f = Fixture(this, suggestions = listOf(suggestion))
        repeat(2) { f.viewModel.next() }

        assertEquals(listOf(suggestion), f.awaitStep(SetupStep.MODEL).suggestions)
    }

    @Test
    fun theContextStepFollowsTheModelStepOnlyOnceThereIsAModel() = runTest(dispatcher) {
        assertEquals(listOf(SetupStep.WELCOME, SetupStep.NETWORK, SetupStep.MODEL, SetupStep.CONTEXT), setupSteps(askNotifications = false, hasModel = true))
        val f = Fixture(this)
        repeat(2) { f.viewModel.next() }
        assertTrue(f.awaitStep(SetupStep.MODEL).isLast)

        f.model.value = true
        assertFalse(f.viewModel.state.first { it.step == SetupStep.MODEL && !it.isLast }.isLast)
        f.viewModel.next()
        assertTrue(f.awaitStep(SetupStep.CONTEXT).isLast)
        f.viewModel.next()
        f.awaitStep(SetupStep.CONTEXT)
    }

    @Test
    fun theContextChoiceIsTheDefaultContextSetting() = runTest(dispatcher) {
        val f = Fixture(this, hasModel = true)
        f.viewModel.setContextLength(8192)
        assertEquals(8192, f.viewModel.state.first { it.contextLength == 8192 }.contextLength)
        assertEquals(8192, f.inference.defaults.first().contextLength)
        f.viewModel.setContextLength(null)
        assertEquals(null, f.inference.defaults.first { it.contextLength == null }.contextLength)
    }

    @Test
    fun networkChoiceIsTheSameSettingAsInSettings() = runTest(dispatcher) {
        val f = Fixture(this)

        f.viewModel.setNetworkMode(NetworkMode.HUGGING_FACE)

        assertEquals(NetworkMode.HUGGING_FACE, f.viewModel.state.first { it.network == NetworkMode.HUGGING_FACE }.network)
        assertEquals(NetworkMode.HUGGING_FACE, f.network.mode.first())
    }

    @Test
    fun finishingRecordsCompletionAndStartsOverNextTime() = runTest(dispatcher) {
        val f = Fixture(this)
        assertFalse(f.setup.complete.first())
        f.viewModel.next()
        f.awaitStep(SetupStep.NETWORK)
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()

        f.viewModel.finish { finished.complete(Unit) }
        finished.await()

        assertTrue(f.setup.complete.first())
        f.awaitStep(SetupStep.WELCOME)
    }
}
