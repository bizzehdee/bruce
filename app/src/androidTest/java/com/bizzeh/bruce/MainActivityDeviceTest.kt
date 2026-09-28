package com.bizzeh.bruce

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val modelsDir = File(instrumentation.targetContext.filesDir, MainActivity.MODELS_DIR)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // The engine and settings outlive a single test, so each test starts from cleared data.
        runBlocking { instrumentation.targetContext.appContainer.dataReset.clearAll() }
        modelsDir.mkdirs()
        instrumentation.context.assets.open("stories260K.gguf").use { input ->
            File(modelsDir, "stories260K.gguf").outputStream().use { input.copyTo(it) }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        modelsDir.deleteRecursively()
    }

    private fun openSettings() {
        compose.waitUntilAtLeastOneExists(hasTestTag("openDrawer"), TIMEOUT_MS)
        compose.onNodeWithTag("openDrawer").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("nav:settings"), TIMEOUT_MS)
        compose.onNodeWithTag("nav:settings").performClick()
    }

    private fun openDiagnostics() {
        openSettings()
        compose.waitUntilAtLeastOneExists(hasTestTag("settings:diagnostics"), TIMEOUT_MS)
        compose.onNodeWithTag("settings:diagnostics").performScrollTo().performClick()
    }

    @Test
    fun chatWithAModelChosenFromTheTitle() {
        compose.waitUntilAtLeastOneExists(hasText("No model loaded"), TIMEOUT_MS)
        compose.onNodeWithTag("chatTitle").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("switch:stories260K.gguf"), TIMEOUT_MS)
        compose.onNodeWithTag("switch:stories260K.gguf").performClick()
        compose.waitUntilAtLeastOneExists(hasText("stories260K"), TIMEOUT_MS)

        compose.onNodeWithTag("composer").performTextInput("Tell me a story")
        compose.onNodeWithTag("send").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Tell me a story"), TIMEOUT_MS)
        compose.waitUntilAtLeastOneExists(hasTestTag("send"), TIMEOUT_MS)
        compose.onNodeWithTag("answer:1").assert(hasNonBlankText)
    }

    @Test
    fun choosingAModelInModelsIsRememberedAcrossRecreation() {
        compose.waitUntilAtLeastOneExists(hasTestTag("openDrawer"), TIMEOUT_MS)
        compose.onNodeWithTag("openDrawer").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("nav:models"), TIMEOUT_MS)
        compose.onNodeWithTag("nav:models").performClick()
        compose.waitUntilAtLeastOneExists(hasText("stories260K"), TIMEOUT_MS)
        compose.onNodeWithText("stories260K").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("use:stories260K.gguf"), TIMEOUT_MS)
        // A synthetic tap after scrolling missed the button on the XZ Premium's shorter screen;
        // the click action itself is what is under test.
        compose.onNodeWithTag("use:stories260K.gguf").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText("In use"), TIMEOUT_MS)

        compose.onNodeWithTag("back").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("chatTitle") and hasText("stories260K"), TIMEOUT_MS)

        scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasTestTag("chatTitle") and hasText("stories260K"), TIMEOUT_MS)
    }

    @Test
    fun loadsAModelAndGeneratesText() {
        openDiagnostics()
        compose.waitUntilAtLeastOneExists(hasTestTag("model:stories260K.gguf"), TIMEOUT_MS)
        compose.onNodeWithTag("model:stories260K.gguf").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Architecture: llama"), TIMEOUT_MS)
        compose.onNodeWithText("CPU").performScrollTo().performClick()
        compose.onNodeWithText("Load").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Loaded on CPU"), TIMEOUT_MS)

        compose.onNodeWithTag("prompt").performScrollTo().performTextInput("Once upon a time")
        compose.onNodeWithText("Generate").performScrollTo().performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("stats"), TIMEOUT_MS)
        compose.onNodeWithTag("output").assert(hasNonBlankText)
    }

    @Test
    fun themeChoiceSurvivesRecreation() {
        openSettings()
        compose.waitUntilAtLeastOneExists(hasText("Dark"), TIMEOUT_MS)
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Dark") and isSelected(), TIMEOUT_MS)

        scenario.recreate()

        compose.waitUntilAtLeastOneExists(hasText("Dark") and isSelected(), TIMEOUT_MS)
        // Recreation keeps the Settings screen, so the choice is visible straight away.
        compose.onNodeWithText("System").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("System") and isSelected(), TIMEOUT_MS)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L

        val hasNonBlankText = SemanticsMatcher("has non-blank text") { node ->
            node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().any { it.isNotBlank() }
        }
    }
}
