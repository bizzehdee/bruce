package com.bizzeh.bruce.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.huggingface.HubAccount
import com.bizzeh.bruce.huggingface.SignInError
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.ui.theme.BruceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tall screen, so every setting is laid out without scrolling nested scroll containers. */
@Config(qualifiers = "w400dp-h2400dp")
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : SettingsActions {
        override fun setThemeMode(mode: ThemeMode) { calls += "theme $mode" }
        override fun setDynamicColour(enabled: Boolean) { calls += "dynamic $enabled" }
        override fun setBackend(backend: BackendPreference) { calls += "backend $backend" }
        override fun setThreads(threads: Int?) { calls += "threads $threads" }
        override fun setContextLength(contextLength: Int) { calls += "context $contextLength" }
        override fun clearAllData() { calls += "clear" }
        override fun deleteAllConversations() { calls += "deleteChats" }
        override fun setNetworkMode(mode: NetworkMode) { calls += "network $mode" }
        override fun setPersonality(personality: Personality) { calls += "personality $personality" }
        override fun setSummaryEnabled(enabled: Boolean) { calls += "summarise $enabled" }
        override fun setSummaryThreshold(threshold: Int) { calls += "threshold $threshold" }
        override fun signIn() { calls += "signIn" }
        override fun signOut() { calls += "signOut" }
        override fun openSkills() { calls += "skills" }
        override fun openPermissions() { calls += "permissions" }
        override fun openLicences() { calls += "licences" }
        override fun openDiagnostics() { calls += "diagnostics" }
    }

    private fun show(state: SettingsState = SettingsState()) =
        compose.setContent { BruceTheme { SettingsScreen(state, actions) {} } }

    @Test
    fun summariseIsOffWithNoThresholdShown() {
        show()
        compose.onNodeWithText("90%").assertDoesNotExist()
        compose.onNodeWithTag("summarise").performScrollTo().performClick()

        assertEquals(listOf("summarise true"), calls)
    }

    @Test
    fun summariseThresholdIsChosenWhenOn() {
        show(SettingsState(summary = SummarySettings(enabled = true, threshold = 90)))
        compose.onNodeWithText("95%").performScrollTo().performClick()

        assertEquals(listOf("threshold 95"), calls)
    }

    @Test
    fun appearanceAndDefaults() {
        show(SettingsState(dynamicColourSupported = true))

        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithTag("dynamicColour").performClick()
        compose.onNodeWithText("CPU").performScrollTo().performClick()
        compose.onNodeWithTag("threads:2").performClick()
        compose.onNodeWithTag("threads:auto").performClick()
        compose.onNodeWithTag("context:8192").performClick()

        assertEquals(listOf("theme DARK", "dynamic true", "backend CPU", "threads 2", "threads null", "context 8192"), calls)
        compose.onNodeWithText("Auto (4)").assertIsDisplayed()
    }

    @Test
    fun networkModeAndSignIn() {
        show(SettingsState(network = NetworkMode.HUGGING_FACE))

        compose.onNodeWithText("Hugging Face").performClick()
        compose.onNodeWithText("Offline").performClick()
        compose.onNodeWithTag("signIn").performClick()

        assertEquals(listOf("network HUGGING_FACE", "network OFFLINE", "signIn"), calls)
    }

    @Test
    fun signInIsUnavailableOffline() {
        show(SettingsState(network = NetworkMode.OFFLINE))

        compose.onNodeWithText("Allow Hugging Face in Network to sign in.").assertIsDisplayed()
        compose.onNodeWithTag("signIn").performClick()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun signedInAccountCanSignOut() {
        show(SettingsState(network = NetworkMode.HUGGING_FACE, account = HubAccount("bruce-owner", 0)))

        compose.onNodeWithText("Signed in as bruce-owner").assertIsDisplayed()
        compose.onNodeWithTag("signOut").performClick()

        assertEquals(listOf("signOut"), calls)
    }

    @Test
    fun signInErrorAndModeSummaries() {
        show(SettingsState(network = NetworkMode.GENERAL, signInError = SignInError.DENIED))

        compose.onNodeWithTag("signInError").assertIsDisplayed()
        compose.onNodeWithText("Any site, once Bruce has skills that use the web.").assertIsDisplayed()
        assertEquals(com.bizzeh.bruce.R.string.network_approved_summary, SettingsText.networkSummary(NetworkMode.APPROVED_DOMAINS))
        assertEquals(com.bizzeh.bruce.R.string.network_approved, SettingsText.networkLabel(NetworkMode.APPROVED_DOMAINS))
        assertEquals("1 Jan 1970, 00:00", SettingsText.date(0, java.util.Locale.UK).let { it.substringBefore(',') + ", 00:00" })
    }

    @Test
    fun onlyOfferedBackendsAreShownAndTheGpuNoteOnlyWithAGpu() {
        show(SettingsState(backends = listOf(BackendPreference.AUTO, BackendPreference.CPU)))

        compose.onNodeWithText("OpenCL").assertDoesNotExist()
        compose.onNodeWithText("Vulkan").assertDoesNotExist()
        compose.onNodeWithText("Auto uses the CPU", substring = true).assertDoesNotExist()
    }

    @Test
    fun gpuNoteShownWhenAGpuIsOffered() {
        show(SettingsState(backends = listOf(BackendPreference.AUTO, BackendPreference.CPU, BackendPreference.VULKAN)))

        compose.onNodeWithText("Vulkan").assertExists()
        compose.onNodeWithText("Auto uses the CPU", substring = true).assertExists()
    }

    @Test
    fun sidekickPersonalityIsChosenHere() {
        show(SettingsState(personality = Personality.BRUCE))

        compose.onNodeWithText("Curious, calm and thoughtful. Observes first, speaks when it helps.").assertExists()
        compose.onNodeWithTag("personality:MILO").performClick()

        assertEquals(listOf("personality MILO"), calls)
    }

    @Test
    fun dynamicColourHiddenWhereUnsupported() {
        show()

        compose.onNodeWithTag("dynamicColour").assertDoesNotExist()
    }

    @Test
    fun clearingDataNeedsConfirmation() {
        show()

        compose.onNodeWithTag("settings:clear").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(calls.isEmpty())

        compose.onNodeWithTag("settings:clear").performScrollTo().performClick()
        compose.onNodeWithTag("confirmClear").performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun deletingAllChatsNeedsConfirmation() {
        show()

        compose.onNodeWithTag("settings:deleteChats").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(calls.isEmpty())

        compose.onNodeWithTag("settings:deleteChats").performScrollTo().performClick()
        compose.onNodeWithTag("confirmDeleteChats").performClick()
        assertEquals(listOf("deleteChats"), calls)
    }

    @Test
    fun links() {
        show()

        compose.onNodeWithTag("settings:skills").performScrollTo().performClick()
        compose.onNodeWithTag("settings:permissions").performScrollTo().performClick()
        compose.onNodeWithTag("settings:licences").performScrollTo().performClick()
        compose.onNodeWithTag("settings:diagnostics").performScrollTo().performClick()

        assertEquals(listOf("skills", "permissions", "licences", "diagnostics"), calls)
    }

    @Test
    fun everyLicenceTextIsBundled() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        OpenSourceLicences.components.forEach { component ->
            val text = resources.openRawResource(component.text).use { it.readBytes().decodeToString() }
            assertTrue(component.name, text.length > 500)
        }
    }
}
