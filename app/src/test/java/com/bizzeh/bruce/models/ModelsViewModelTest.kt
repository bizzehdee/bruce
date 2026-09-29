package com.bizzeh.bruce.models

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bizzeh.bruce.R
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.settings.InferenceDefaults
import com.bizzeh.bruce.settings.InferenceSettingsRepository
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ModelsViewModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private var importResult: ImportResult = ImportResult.Failed(ImportError.NOT_GGUF)
    private val cpu = CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private inner class Fixture(scope: TestScope) {
        val modelsDir = temp.newFolder("models")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(temp.root, "s.preferences_pb") }
        val settings = ModelSettingsRepository(dataStore)
        val activeModel = ActiveModel(engine, modelsDir, dispatcher)
        val selection = ModelSelection(activeModel, settings, InferenceSettingsRepository(dataStore), modelsDir, dispatcher)
        val viewModel = ModelsViewModel(
            activeModel, selection, settings, flowOf(InferenceDefaults()), { importResult },
            { DeviceProfile(4L shl 30, cpu) }, dispatcher,
            templateSupportsTools = { template, bos, eos -> judged += Triple(template, bos, eos); "tools" in template },
        )
        val judged = mutableListOf<Triple<String, String?, String?>>()
        fun stories() = File(modelsDir, "stories260K.gguf").apply { writeBytes(File("src/androidTest/assets/stories260K.gguf").readBytes()) }
    }

    @Test
    fun installedModelsSayWhetherTheirTemplateSupportsSkills() = runTest(dispatcher) {
        val f = Fixture(this)
        File(f.modelsDir, "full.gguf").writeBytes(com.bizzeh.bruce.gguf.GgufBuilder().string("tokenizer.chat_template", "{{ tools }}").build())
        File(f.modelsDir, "stripped.gguf").writeBytes(com.bizzeh.bruce.gguf.GgufBuilder().string("tokenizer.chat_template", "{{ messages }}").build())
        File(f.modelsDir, "none.gguf").writeBytes(com.bizzeh.bruce.gguf.GgufBuilder().build())

        f.viewModel.refresh()

        assertEquals(mapOf("full.gguf" to true, "none.gguf" to null, "stripped.gguf" to false), f.viewModel.state.value.models.associate { it.file.name to it.skills })
        assertEquals("a file has token ids, not BOS/EOS text", listOf(Triple("{{ tools }}", null, null), Triple("{{ messages }}", null, null)), f.judged)
    }

    @Test
    fun listsInstalledModelsWithAssessment() = runTest(dispatcher) {
        val f = Fixture(this)
        f.stories()
        File(f.modelsDir, "broken.gguf").writeText("nope")

        f.viewModel.refresh()

        val models = f.viewModel.state.value.models
        assertEquals(listOf("broken.gguf", "stories260K.gguf"), models.map { it.file.name })
        assertNull(models[0].metadata)
        assertFalse(models[0].assessment.supported)
        assertEquals("llama", models[1].metadata?.architecture)
        assertEquals(Fit.FITS, models[1].assessment.fit)
        assertEquals(2048, models[1].assessment.estimate.contextLength)
    }

    @Test
    fun chooseLoadsAndTracksActiveAndFailure() = runTest(dispatcher) {
        val f = Fixture(this)
        val file = f.stories()
        engine.loadResult = LoadResult.Loaded(ModelInfo("m", 1, 1, 1), Backend.CPU)

        // Choosing writes to DataStore on real I/O threads, so wait for the resulting state.
        f.viewModel.choose(file)
        val chosen = f.viewModel.state.first { it.active == file && it.loading == null }
        assertNull(chosen.loadError)

        engine.loadResult = LoadResult.Failed(LoadError.CONTEXT_CREATION_FAILED)
        f.viewModel.choose(file)
        assertEquals(LoadError.CONTEXT_CREATION_FAILED, f.viewModel.state.first { it.loadError != null }.loadError)
    }

    @Test
    fun importRefreshesOrReportsFailure() = runTest(dispatcher) {
        val f = Fixture(this)
        f.viewModel.import(Uri.parse("content://docs/1"))
        advanceUntilIdle()
        assertEquals(ImportError.NOT_GGUF, f.viewModel.state.value.importError)

        importResult = ImportResult.Imported(f.stories())
        f.viewModel.import(Uri.parse("content://docs/2"))
        val imported = f.viewModel.state.first { it.models.size == 1 }
        assertNull(imported.importError)
        assertFalse(imported.importing)
    }

    @Test
    fun overridesAndDeletion() = runTest(dispatcher) {
        val f = Fixture(this)
        val file = f.stories()
        f.viewModel.refresh()

        f.viewModel.setOverrides(file, ModelOverrides(contextLength = 8192))
        val updated = f.viewModel.state.first { it.models.singleOrNull()?.overrides?.contextLength == 8192 }.models.single()
        // Capped at the model's trained 2048 tokens, as loading does.
        assertEquals(2048, updated.assessment.estimate.contextLength)

        f.viewModel.delete(file)
        assertTrue(f.viewModel.state.first { it.models.isEmpty() }.models.isEmpty())
    }

    @Test
    fun text() {
        val model = InstalledModel(
            File("m.gguf"), 1_185_376, null,
            ModelFit.assess(Candidate("a/b", "m-Q4_0.gguf", 1_185_376, false, null, null), DeviceProfile(1L shl 30, cpu), 2048),
            ModelOverrides(),
        )
        assertEquals("1.1 MB · Q4_0", ModelsText.summary(model))
        assertEquals(R.string.models_unsupported, ModelsText.fitLabel(model.assessment))
        assertEquals(listOf("Not a readable GGUF file."), ModelsText.details(model))
        assertEquals(R.string.models_speed_slow, ModelsText.speedLabel(SpeedBand.SLOW))
        assertEquals(R.string.models_speed_usable, ModelsText.speedLabel(SpeedBand.USABLE))
        assertEquals(R.string.models_speed_fast, ModelsText.speedLabel(SpeedBand.FAST))
        assertEquals("100+ tok/s", ModelsText.speed(model.assessment))
        assertEquals("~20 tok/s", ModelsText.speed(model.assessment.copy(expectedTokensPerSecond = 20.2)))
    }
}
