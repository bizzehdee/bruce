package com.bizzeh.bruce.prototype

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.models.MemoryCheck
import com.bizzeh.bruce.models.MemoryEstimate
import com.bizzeh.bruce.ui.theme.BruceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PrototypeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : PrototypeActions {
        override fun importModel() { calls += "import" }
        override fun select(file: File) { calls += "select ${file.name}" }
        override fun setBackend(backend: BackendPreference) { calls += "backend $backend" }
        override fun load() { calls += "load" }
        override fun setPrompt(prompt: String) { calls += "prompt $prompt" }
        override fun generate() { calls += "generate" }
        override fun stop() { calls += "stop" }
    }

    private fun show(state: PrototypeState) = compose.setContent { BruceTheme { PrototypeScreen(state, actions) } }

    @Test
    fun backArrowWhenReachedFromSettings() {
        var back = 0
        compose.setContent { BruceTheme { PrototypeScreen(PrototypeState(), actions) { back++ } } }

        compose.onNodeWithTag("back").performClick()

        assertEquals(1, back)
    }

    @Test
    fun emptyStateOffersImport() {
        show(PrototypeState())

        compose.onNodeWithText("No models yet.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Import GGUF").performScrollTo().performClick()

        assertEquals(listOf("import"), calls)
    }

    @Test
    fun selectingModelBackendAndLoading() {
        val model = File("m.gguf")
        show(PrototypeState(models = listOf(model), selected = model))

        compose.onNodeWithTag("model:m.gguf").performScrollTo().performClick()
        compose.onNodeWithText("CPU").performScrollTo().performClick()
        compose.onNodeWithText("Load").performScrollTo().performClick()

        assertEquals(listOf("select m.gguf", "backend CPU", "load"), calls)
    }

    @Test
    fun memoryWarningShownWhenModelMayNotFit() {
        val model = File("m.gguf")
        val check = MemoryCheck(MemoryEstimate(2_000, 0, 256), usableBytes = 1_000)

        show(PrototypeState(models = listOf(model), selected = model, memory = check))

        compose.onNodeWithText("Warning: this model may not fit in memory.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loadedModelCanGenerateAndStop() {
        val model = File("m.gguf")
        show(
            PrototypeState(
                models = listOf(model),
                selected = model,
                load = LoadState.Loaded(ModelInfo("m", 1, 1, 1), Backend.CPU, emptyList()),
                generating = true,
                output = "Once upon",
            ),
        )

        compose.onNodeWithTag("prompt").performScrollTo().performTextInput("Hi")
        compose.onNodeWithText("Stop").performScrollTo().performClick()

        compose.onNodeWithTag("output").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("prompt Hi", "stop"), calls)
    }

    @Test
    fun generateWhenIdle() {
        val model = File("m.gguf")
        show(PrototypeState(selected = model, load = LoadState.Loaded(ModelInfo("m", 1, 1, 1), Backend.CPU, emptyList())))

        compose.onNodeWithText("Generate").performScrollTo().performClick()

        assertEquals(listOf("generate"), calls)
    }
}
