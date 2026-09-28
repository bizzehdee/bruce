package com.bizzeh.bruce.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
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
@Config(qualifiers = "w400dp-h1600dp")
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
        override fun openPermissions() { calls += "permissions" }
        override fun openLicences() { calls += "licences" }
        override fun openDiagnostics() { calls += "diagnostics" }
    }

    private fun show(state: SettingsState = SettingsState()) =
        compose.setContent { BruceTheme { SettingsScreen(state, actions) {} } }

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
    fun links() {
        show()

        compose.onNodeWithTag("settings:permissions").performScrollTo().performClick()
        compose.onNodeWithTag("settings:licences").performScrollTo().performClick()
        compose.onNodeWithTag("settings:diagnostics").performScrollTo().performClick()

        assertEquals(listOf("permissions", "licences", "diagnostics"), calls)
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
