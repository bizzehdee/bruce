package com.bizzeh.bruce

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
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test
    fun loadsAModelAndGeneratesText() {
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
        compose.waitUntilAtLeastOneExists(hasText("Dark"), TIMEOUT_MS)
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Dark") and isSelected(), TIMEOUT_MS)

        scenario.recreate()

        compose.waitUntilAtLeastOneExists(hasText("Dark") and isSelected(), TIMEOUT_MS)
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
