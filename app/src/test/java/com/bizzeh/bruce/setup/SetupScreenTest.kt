package com.bizzeh.bruce.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bizzeh.bruce.models.ImportError
import com.bizzeh.bruce.models.InstalledModel
import com.bizzeh.bruce.models.ModelOverrides
import com.bizzeh.bruce.models.ModelsState
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.ui.theme.BruceTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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
        override fun finish(exit: SetupExit) { calls += "finish $exit" }
    }

    private fun show(state: SetupState, models: ModelsState = ModelsState()) =
        compose.setContent { BruceTheme { SetupScreen(state, models, actions) } }

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
}
