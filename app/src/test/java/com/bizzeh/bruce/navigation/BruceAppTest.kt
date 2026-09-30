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
        override fun selectRemote(choice: com.bizzeh.bruce.models.RemoteChoice) { calls += "remote ${choice.label}" }
        override fun chooseProfile(id: String?) { calls += "profile $id" }
        override fun saveProfile(name: String) { calls += "save profile $name" }
        override fun removeProfile(id: String) { calls += "remove profile $id" }
    }
    private val qwen = File("Qwen3-0.6B-Q4_0.gguf")
    private val models = ActiveModelState(installed = listOf(qwen, File("stories.gguf")), active = qwen)

    private val noGrantActions = object : com.bizzeh.bruce.settings.GrantActions {
        override fun addFolder() = Unit
        override fun addFile() = Unit
        override fun revoke(grant: com.bizzeh.bruce.policy.Grant) = Unit
    }

    private fun show(start: Destination = Destination.CHAT, profiles: List<com.bizzeh.bruce.models.Profile> = emptyList()) = compose.setContent {
        BruceTheme {
            BruceApp(
                chat = ChatState(modelName = "Qwen3-0.6B-Q4_0", profileId = profiles.firstOrNull()?.id),
                profiles = profiles,
                chatActions = chatActions,
                activeModel = models,
                actions = appActions,
                remoteModels = listOf(com.bizzeh.bruce.models.RemoteChoice("s", "Home", "qwen3-8b")),
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
                memoryScreen = { onBack -> SubScreen("Memory screen", onBack) { Text("memory body") } },
                permissionsScreen = { onBack, openNetwork ->
                    com.bizzeh.bruce.settings.PermissionsScreen(emptyList(), false, noGrantActions, openNetwork, onBack)
                },
                skillsScreen = { onBack, openPermissions, openSettings ->
                    SubScreen("Skills screen", onBack) {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.TextButton(onClick = openPermissions, modifier = androidx.compose.ui.Modifier.testTag("skills:permissions")) { Text("To permissions") }
                            androidx.compose.material3.TextButton(onClick = openSettings, modifier = androidx.compose.ui.Modifier.testTag("skills:settings")) { Text("To settings") }
                        }
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
        compose.onNodeWithText("qwen3-8b (Home)").assertExists()
        compose.onNodeWithTag("switch:stories.gguf").performClick()
        compose.onNodeWithTag("chatTitle").performClick()
        compose.onNodeWithTag("switchRemote:s:qwen3-8b").performClick()

        assertEquals(listOf("select stories.gguf", "remote qwen3-8b (Home)"), calls)
    }

    @Test
    fun profilesArePickedSavedAndRemovedInTheSwitcher() {
        show(profiles = listOf(com.bizzeh.bruce.models.Profile("p1", "Fast", com.bizzeh.bruce.models.ProfileModel.Local("stories.gguf"))))

        compose.onNodeWithTag("chatTitle").performClick()
        compose.onNodeWithTag("profile:Fast").performClick()
        compose.onNodeWithTag("chatTitle").performClick()
        compose.onNodeWithTag("removeProfile:Fast").performClick()
        compose.onNodeWithTag("saveProfile").performClick()
        compose.onNodeWithText("Coding").performClick()
        compose.onNodeWithTag("confirmProfile").performClick()

        assertEquals(listOf("profile p1", "remove profile p1", "save profile Coding"), calls)
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

        compose.onNodeWithTag("settings:skills").performClick()
        compose.onNodeWithTag("skills:settings").performClick()
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
        assertEquals(Destination.SETTINGS, backTarget(Destination.MEMORY))
    }
}
