package com.bizzeh.bruce.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bizzeh.bruce.R
import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.models.DeviceTier
import com.bizzeh.bruce.models.DownloadState
import com.bizzeh.bruce.models.ImportError
import com.bizzeh.bruce.models.InstalledModel
import com.bizzeh.bruce.models.ModelOverrides
import com.bizzeh.bruce.models.ModelsState
import com.bizzeh.bruce.models.SuggestedModel
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.ui.theme.BruceTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(qualifiers = "w400dp-h2000dp")
@RunWith(RobolectricTestRunner::class)
class SetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : SetupActions {
        override fun next() { calls += "next" }
        override fun back() { calls += "back" }
        override fun setNetworkMode(mode: NetworkMode) { calls += "network $mode" }
        override fun allowNotifications() { calls += "notifications" }
        override fun importModel() { calls += "import" }
        override fun download(model: SuggestedModel) { calls += "download ${model.path}" }
        override fun cancelDownload(model: SuggestedModel) { calls += "cancel ${model.path}" }
        override fun setContextLength(contextLength: Int?) { calls += "context $contextLength" }
        override fun setSkillGroup(kind: com.bizzeh.bruce.skills.CapabilityClass, on: Boolean) { calls += "skills $kind $on" }
        override fun finish(exit: SetupExit) { calls += "finish $exit" }
    }

    private fun show(state: SetupState, models: ModelsState = ModelsState(), downloads: Map<String, DownloadState> = emptyMap()) =
        compose.setContent { BruceTheme { SetupScreen(state, models, actions, downloads) } }

    private fun suggestion(path: String) = SuggestedModel("a/repo", path, 1_500_000_000, "Model $path", R.string.suggested_qwen3_1_7b, setOf(DeviceTier.EIGHT_GB))

    @Test
    fun welcomeHasNoBack() {
        show(SetupState(SetupStep.WELCOME))

        compose.onNodeWithText("Welcome to Bruce").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("setupBack")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("setupNext").performClick()

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun networkStepSetsTheMode() {
        show(SetupState(SetupStep.NETWORK, isFirst = false))

        compose.onNodeWithTag("setupNetwork:HUGGING_FACE").performClick()
        compose.onNodeWithTag("setupBack").performClick()

        assertEquals(listOf("network HUGGING_FACE", "back"), calls)
    }

    @Test
    fun notificationsCanBeAllowedOrPostponed() {
        show(SetupState(SetupStep.NOTIFICATIONS, isFirst = false))

        compose.onNodeWithTag("setupAllowNotifications").performClick()
        compose.onNodeWithText("Not now").performClick()

        assertEquals(listOf("notifications", "next"), calls)
    }

    @Test
    fun offlineModelStepOffersImportAndSkip() {
        show(SetupState(SetupStep.MODEL, isFirst = false, isLast = true), ModelsState(importError = ImportError.NOT_GGUF))

        compose.onNodeWithTag("setupBrowse").assertIsNotEnabled()
        compose.onNodeWithText("To download, go back and allow Hugging Face.").assertIsDisplayed()
        compose.onNodeWithTag("setupImportError").assertIsDisplayed()
        compose.onNodeWithTag("setupImport").performClick()
        compose.onNodeWithText("Skip for now").performClick()

        assertEquals(listOf("import", "finish CHAT"), calls)
    }

    @Test
    fun onlineModelStepOffersDownloadAndShowsInstalledModels() {
        val model = InstalledModel(File("stories260K.gguf"), 1, null, mockk(), ModelOverrides())
        show(SetupState(SetupStep.MODEL, NetworkMode.HUGGING_FACE, isFirst = false, isLast = true), ModelsState(models = listOf(model)))

        compose.onNodeWithText("Installed: stories260K").assertIsDisplayed()
        compose.onNodeWithTag("setupBrowse").assertIsEnabled().performClick()
        compose.onNodeWithText("Start chatting").performClick()

        assertEquals(listOf("finish BROWSE_MODELS", "finish CHAT"), calls)
    }

    @Test
    fun importButtonIsDisabledWhileImporting() {
        show(SetupState(SetupStep.MODEL, isFirst = false, isLast = true), ModelsState(importing = true))

        compose.onNodeWithTag("setupImport").assertIsNotEnabled()
        compose.onNodeWithText("Importing…").assertIsDisplayed()
    }

    @Test
    fun suggestedModelsShowWhatTheyAreGoodAtAndDownloadInOneTap() {
        val fresh = suggestion("a.gguf")
        val running = suggestion("b.gguf")
        val failed = suggestion("c.gguf")
        val installed = suggestion("d.gguf")
        val tooLarge = suggestion("e.gguf")
        val model = InstalledModel(File("d.gguf"), 1, null, mockk(), ModelOverrides())
        show(
            SetupState(SetupStep.MODEL, NetworkMode.HUGGING_FACE, isFirst = false, isLast = true, suggestions = listOf(fresh, running, failed, installed, tooLarge)),
            ModelsState(models = listOf(model)),
            mapOf(
                "a/repo/b.gguf" to DownloadState(50, 100),
                "a/repo/c.gguf" to DownloadState(0, 0, DownloadError.INTERRUPTED),
                "a/repo/e.gguf" to DownloadState(0, 0, DownloadError.TOO_LARGE, neededBytes = 9_000_000_000, freeBytes = 3_000_000_000),
            ),
        )

        compose.onNodeWithText("Suggested for this phone").assertIsDisplayed()
        compose.onNodeWithText("Model a.gguf · 1.40 GB").assertIsDisplayed()
        compose.onAllNodesWithText("Good all-rounder", substring = true).assertCountEquals(5)
        compose.onNodeWithTag("suggestedDownload:a.gguf").performClick()
        compose.onNodeWithTag("suggestedProgress:b.gguf").assertIsDisplayed()
        compose.onNodeWithTag("suggestedCancel:b.gguf").performClick()
        compose.onNodeWithText("Download stopped: INTERRUPTED").assertIsDisplayed()
        compose.onNodeWithTag("suggestedRetry:c.gguf").performClick()
        compose.onNodeWithTag("suggestedInstalled:d.gguf").assertIsDisplayed()
        compose.onNodeWithText("Not downloaded", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Choose another on Hugging Face").performClick()

        assertEquals(listOf("download a.gguf", "cancel b.gguf", "download c.gguf", "finish BROWSE_MODELS"), calls)
    }

    @Test
    fun offlineSuggestionsShowButCannotDownload() {
        show(SetupState(SetupStep.MODEL, isFirst = false, isLast = true, suggestions = listOf(suggestion("a.gguf"))))

        compose.onNodeWithTag("suggestedDownload:a.gguf").assertIsNotEnabled()
        compose.onNodeWithText("To download, go back and allow Hugging Face.").assertIsDisplayed()
        compose.onNodeWithText("Skip for now").performClick()

        assertEquals(listOf("finish CHAT"), calls)
    }

    @Test
    fun withoutSuggestionsTheStepIsAsBefore() {
        show(SetupState(SetupStep.MODEL, NetworkMode.HUGGING_FACE, isFirst = false, isLast = true))

        assertEquals(0, compose.onAllNodesWithText("Suggested for this phone").fetchSemanticsNodes().size)
        compose.onNodeWithText("Download from Hugging Face").assertIsDisplayed()
    }

    @Test
    fun theContextStepOffersAutoAndTheSizesThatFit() {
        val choice = ContextChoice("Qwen3 1.7B", 8192, listOf(com.bizzeh.bruce.models.ContextOption(4096, 1_500_000_000), com.bizzeh.bruce.models.ContextOption(8192, 1_700_000_000)))
        compose.setContent { BruceTheme { SetupScreen(SetupState(SetupStep.CONTEXT, isFirst = false, isLast = true), ModelsState(), actions, contextChoice = choice) } }

        compose.onNodeWithText("For Qwen3 1.7B:").assertIsDisplayed()
        compose.onNodeWithTag("setupContext:auto").assertIsSelected()
        compose.onNodeWithText("Auto (8K)").assertIsDisplayed()
        compose.onNodeWithText("4K · 1.40 GB").performClick()
        compose.onNodeWithTag("setupContext:16384").assertDoesNotExist()

        assertEquals(listOf("context 4096"), calls)
    }

    @Test
    fun theSkillsStepTurnsKindsOnAndWarnsWhenTheyCrowdTheContext() {
        val clock = com.bizzeh.bruce.skills.Skill("get_datetime", 1, "time", com.bizzeh.bruce.skills.InputSchema(), setOf(com.bizzeh.bruce.skills.Capability.TIME), com.bizzeh.bruce.skills.SkillState.ACCEPTED) { com.bizzeh.bruce.skills.SkillOutcome.Done("") }
        val groups = listOf(SkillGroup(com.bizzeh.bruce.skills.CapabilityClass.INFORMATION, listOf(clock), on = true))
        compose.setContent { BruceTheme { SetupScreen(SetupState(SetupStep.SKILLS, isFirst = false, isLast = true, skillGroups = groups, promptTokens = 1200, contextLength = 4096), ModelsState(), actions) } }

        compose.onNodeWithText("Phone status").assertIsDisplayed()
        compose.onNodeWithTag("setupSkillsCrowded").assertIsDisplayed()
        compose.onNodeWithTag("setupSkills:INFORMATION").performClick()

        assertEquals(listOf("skills INFORMATION false"), calls)
    }

    @Test
    fun noWarningWhileTheInstructionsFitAQuarter() {
        compose.setContent { BruceTheme { SetupScreen(SetupState(SetupStep.SKILLS, isFirst = false, isLast = true, promptTokens = 900, contextLength = 4096), ModelsState(), actions) } }

        compose.onNodeWithTag("setupSkillsCrowded").assertDoesNotExist()
    }
}
