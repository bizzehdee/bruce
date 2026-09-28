package com.bizzeh.bruce.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import com.bizzeh.bruce.chat.ChatActions
import com.bizzeh.bruce.chat.ChatState
import com.bizzeh.bruce.models.ActiveModelState
import com.bizzeh.bruce.ui.theme.BruceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BruceAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val chatActions = object : ChatActions {
        override fun setInput(input: String) = Unit
        override fun send() = Unit
        override fun stop() = Unit
        override fun decide(callId: String, approved: Boolean) { }
    }
    private val appActions = object : AppActions {
        override fun newChat() { calls += "newChat" }
        override fun selectModel(file: File) { calls += "select ${file.name}" }
    }
    private val qwen = File("Qwen3-0.6B-Q4_0.gguf")
    private val models = ActiveModelState(installed = listOf(qwen, File("stories.gguf")), active = qwen)

    private val noGrantActions = object : com.bizzeh.bruce.settings.GrantActions {
        override fun addFolder() = Unit
        override fun addFile() = Unit
        override fun revoke(grant: com.bizzeh.bruce.policy.Grant) = Unit
    }

    private fun show(start: Destination = Destination.CHAT) = compose.setContent {
        BruceTheme {
            BruceApp(
                chat = ChatState(modelName = "Qwen3-0.6B-Q4_0"),
                chatActions = chatActions,
                activeModel = models,
                actions = appActions,
                modelsScreen = { onBack -> SubScreen("Models screen", onBack) { Text("models body") } },
                settingsScreen = { onBack, open ->
                    SubScreen("Settings screen", onBack) {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.TextButton(onClick = { open(Destination.DIAGNOSTICS) }, modifier = androidx.compose.ui.Modifier.testTag("settings:diagnostics")) { Text("Diagnostics") }
                            androidx.compose.material3.TextButton(onClick = { open(Destination.LICENCES) }, modifier = androidx.compose.ui.Modifier.testTag("settings:licences")) { Text("Licences") }
                            androidx.compose.material3.TextButton(onClick = { open(Destination.PERMISSIONS) }, modifier = androidx.compose.ui.Modifier.testTag("settings:permissions")) { Text("Permissions") }
                            androidx.compose.material3.TextButton(onClick = { open(Destination.SKILLS) }, modifier = androidx.compose.ui.Modifier.testTag("settings:skills")) { Text("Skills") }
                        }
                    }
                },
                diagnosticsScreen = { onBack -> SubScreen("Diagnostics screen", onBack) { Text("diagnostics body") } },
                permissionsScreen = { onBack, openNetwork ->
                    com.bizzeh.bruce.settings.PermissionsScreen(emptyList(), false, noGrantActions, openNetwork, onBack)
                },
                skillsScreen = { onBack, openPermissions ->
                    SubScreen("Skills screen", onBack) {
                        androidx.compose.material3.TextButton(onClick = openPermissions, modifier = androidx.compose.ui.Modifier.testTag("skills:permissions")) { Text("To permissions") }
                    }
                },
                startDestination = start,
            )
        }
    }

    private fun openDrawer() = compose.onNodeWithTag("openDrawer").performClick()

    @Test
    fun openingTheDrawerPutsTheKeyboardAway() {
        show()
        compose.onNodeWithTag("composer").performClick()
        compose.onNodeWithTag("composer").assertIsFocused()

        openDrawer()

        compose.onNodeWithTag("composer").assertIsNotFocused()
    }

    @Test
    fun chatIsTheStartScreen() {
        show()

        compose.onNodeWithText("Qwen3-0.6B-Q4_0").assertIsDisplayed()
    }

    @Test
    fun drawerLeadsToModelsAndSettingsButNotPermissions() {
        show()
        openDrawer()

        compose.onNodeWithText("Permissions").assertDoesNotExist()
        compose.onNodeWithTag("nav:models").performClick()
        compose.onNodeWithText("models body").assertIsDisplayed()

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("Qwen3-0.6B-Q4_0").assertIsDisplayed()
    }

    @Test
    fun canStartOnModelsAndBackGoesToChat() {
        show(Destination.MODELS)

        compose.onNodeWithText("models body").assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("Qwen3-0.6B-Q4_0").assertIsDisplayed()
    }

    @Test
    fun settingsLeadsToDiagnosticsAndBackStepsOut() {
        show()
        openDrawer()
        compose.onNodeWithTag("nav:settings").performClick()
        compose.onNodeWithTag("settings:diagnostics").performClick()
        compose.onNodeWithText("diagnostics body").assertIsDisplayed()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("settings:diagnostics").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("Qwen3-0.6B-Q4_0").assertIsDisplayed()
    }

    @Test
    fun newChatFromTheDrawer() {
        show()
        openDrawer()

        compose.onNodeWithText("New chat").performClick()

        assertEquals(listOf("newChat"), calls)
    }

    @Test
    fun titleOpensTheModelSwitcher() {
        show()

        compose.onNodeWithTag("chatTitle").performClick()
        compose.onNodeWithTag("switch:stories.gguf").performClick()

        assertEquals(listOf("select stories.gguf"), calls)
    }

    @Test
    fun switcherLeadsToModelManagement() {
        show()

        compose.onNodeWithTag("chatTitle").performClick()
        compose.onNodeWithTag("manageModels").performClick()

        compose.onNodeWithText("models body").assertIsDisplayed()
    }

    @Test
    fun settingsLeadsToLicencesAndPermissions() {
        show()
        openDrawer()
        compose.onNodeWithTag("nav:settings").performClick()

        compose.onNodeWithTag("settings:licences").performClick()
        compose.onNodeWithTag("licence:0").performClick()
        compose.onNodeWithTag("licenceText").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("licence:0").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()

        compose.onNodeWithTag("settings:permissions").performClick()
        compose.onNodeWithTag("grantsEmpty").assertIsDisplayed()
    }

    @Test
    fun skillsLeadToPermissionsAndBackGoesToSettings() {
        show(Destination.SETTINGS)

        compose.onNodeWithTag("settings:skills").performClick()
        compose.onNodeWithTag("skills:permissions").performClick()
        compose.onNodeWithTag("grantsEmpty").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("Settings screen").assertIsDisplayed()
    }

    @Test
    fun backTargets() {
        assertNull(backTarget(Destination.CHAT))
        assertEquals(Destination.CHAT, backTarget(Destination.MODELS))
        assertEquals(Destination.CHAT, backTarget(Destination.SETTINGS))
        assertEquals(Destination.SETTINGS, backTarget(Destination.DIAGNOSTICS))
        assertEquals(Destination.SETTINGS, backTarget(Destination.LICENCES))
        assertEquals(Destination.SETTINGS, backTarget(Destination.PERMISSIONS))
        assertEquals(Destination.SETTINGS, backTarget(Destination.SKILLS))
    }
}
