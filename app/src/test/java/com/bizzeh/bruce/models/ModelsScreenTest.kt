package com.bizzeh.bruce.models

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.ui.theme.BruceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(qualifiers = "w400dp-h2000dp")
@RunWith(RobolectricTestRunner::class)
class ModelsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : ModelsActions {
        override fun choose(file: File) { calls += "choose ${file.name}" }
        override fun importModel() { calls += "import" }
        override fun delete(file: File) { calls += "delete ${file.name}" }
        override fun setOverrides(file: File, overrides: ModelOverrides) { calls += "overrides $overrides" }
        override fun findCopies(query: String) { calls += "find $query" }
    }
    private val fixture = File("src/androidTest/assets/stories260K.gguf")
    private val metadata = (GgufReader.read(fixture) as GgufReadResult.Read).metadata
    private val cpu = CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false)
    private val stories = InstalledModel(
        file = File("stories260K.gguf"),
        sizeBytes = fixture.length(),
        metadata = metadata,
        assessment = ModelFit.assess(Candidate("a/b", "stories260K.gguf", fixture.length(), false, "llama", metadata.parameterCount, metadata), DeviceProfile(4L shl 30, cpu), 2048),
        overrides = ModelOverrides(),
    )

    private fun show(state: ModelsState) = compose.setContent { BruceTheme { ModelsScreen(state, actions, onBack = {}) } }

    @Test
    fun modelSettingsOfferOnlyTheBackendsGiven() {
        compose.setContent {
            BruceTheme { ModelsScreen(ModelsState(models = listOf(stories)), actions, onBack = {}, backendChoices = { listOf(BackendPreference.AUTO, BackendPreference.CPU) }) }
        }

        compose.onNodeWithText("stories260K").performClick()
        compose.onNodeWithTag("backend:CPU").assertExists()
        compose.onNodeWithTag("backend:OPENCL").assertDoesNotExist()
        compose.onNodeWithText("Auto uses the CPU", substring = true).assertDoesNotExist()
    }

    @Test
    fun aModelWithoutSkillSupportSaysSoAndFindsOtherCopies() {
        val limited = stories.copy(skills = false, metadata = stories.metadata?.copy(name = "Llama 3.2 1B Instruct"))
        val browse = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
            override fun openRepository(model: com.bizzeh.bruce.huggingface.HubModel) = Unit
            override fun download(model: com.bizzeh.bruce.huggingface.HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        compose.setContent { BruceTheme { ModelsScreen(ModelsState(models = listOf(limited, stories)), actions, onBack = {}, browseActions = browse) } }

        compose.onNodeWithTag("limitedSkills:stories260K.gguf", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("findCopies:stories260K.gguf").performClick()

        assertEquals(listOf("find Llama-3.2-1B-Instruct"), calls)
        compose.onNodeWithTag("browseQuery").assertIsDisplayed()
    }

    @Test
    fun withoutTheBrowserThereIsNothingToSuggest() {
        show(ModelsState(models = listOf(stories.copy(skills = false))))

        compose.onNodeWithTag("limitedSkills:stories260K.gguf", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("findCopies:stories260K.gguf").assertDoesNotExist()
        assertEquals("stories260K", ModelsText.copySearch(stories.copy(metadata = null)))
    }

    @Test
    fun emptyStateOffersImport() {
        show(ModelsState())

        compose.onNodeWithTag("modelsEmpty").assertIsDisplayed()
        compose.onNodeWithTag("import").performClick()

        assertEquals(listOf("import"), calls)
    }

    @Test
    fun modelShowsFitAndExpandsToDetailsAndSettings() {
        show(ModelsState(models = listOf(stories)))

        compose.onNodeWithText("Fits").assertIsDisplayed()
        compose.onNodeWithText("stories260K").performClick()
        compose.onNodeWithText("Architecture: llama").assertIsDisplayed()
        compose.onNodeWithTag("backend:CPU").performClick()
        compose.onNodeWithTag("threads:2").performClick()
        compose.onNodeWithTag("context:8192").performClick()
        compose.onNodeWithTag("temperature:0.3").performClick()
        compose.onNodeWithTag("use:stories260K.gguf").performClick()

        assertEquals(
            listOf(
                "overrides ${ModelOverrides(backend = BackendPreference.CPU)}",
                "overrides ${ModelOverrides(threads = 2)}",
                "overrides ${ModelOverrides(contextLength = 8192)}",
                "overrides ${ModelOverrides(temperature = 0.3f)}",
                "choose stories260K.gguf",
            ),
            calls,
        )
    }

    @Test
    fun activeModelCannotBeChosenAgain() {
        show(ModelsState(models = listOf(stories), active = stories.file))

        compose.onNodeWithText("In use").assertIsDisplayed()
        compose.onNodeWithText("stories260K").performClick()
        compose.onNodeWithTag("use:stories260K.gguf").assertIsNotEnabled()
    }

    @Test
    fun theActiveModelsTightContextOverrideIsCalledOut() {
        val tight = stories.copy(overrides = ModelOverrides(contextLength = 2048))
        val other = tight.copy(file = File(tight.file.parentFile, "other.gguf"))
        compose.setContent {
            BruceTheme { ModelsScreen(ModelsState(models = listOf(tight, other), active = tight.file), actions, onBack = {}, fixedPromptTokens = 1382) }
        }

        compose.onNodeWithText("stories260K").performClick()
        compose.onNodeWithTag("contextTight", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("stories260K").performClick()
        compose.onNodeWithText("other").performClick()
        compose.onNodeWithTag("contextTight", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun deleteNeedsConfirmation() {
        show(ModelsState(models = listOf(stories)))
        compose.onNodeWithText("stories260K").performClick()

        compose.onNodeWithTag("delete:stories260K.gguf").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(calls.isEmpty())

        compose.onNodeWithTag("delete:stories260K.gguf").performClick()
        compose.onNodeWithTag("confirmDelete").performClick()
        assertEquals(listOf("delete stories260K.gguf"), calls)
    }

    @Test
    fun errorsAreShown() {
        show(ModelsState(models = listOf(stories), loadError = com.bizzeh.bruce.inference.LoadError.MODEL_LOAD_FAILED, importError = ImportError.NOT_GGUF))

        compose.onNodeWithTag("loadError").assertIsDisplayed()
        compose.onNodeWithText("Import failed: NOT_GGUF").assertIsDisplayed()
    }
}
