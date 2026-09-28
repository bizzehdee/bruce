package com.bizzeh.bruce.models

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.settings.InferenceDefaults
import com.bizzeh.bruce.settings.InferenceSettingsRepository
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelSettingsTest {
    @TempDir
    lateinit var dir: File

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()

    private inner class Fixture(scope: TestScope) {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(dir, "s.preferences_pb") }
        val modelsDir = File(dir, "models").apply { mkdirs() }
        val settings = ModelSettingsRepository(dataStore)
        val inference = InferenceSettingsRepository(dataStore)
        val activeModel = ActiveModel(engine, modelsDir, dispatcher)
        val selection = ModelSelection(activeModel, settings, inference, modelsDir, dispatcher)
        fun model(name: String) = File(modelsDir, name).apply { writeText("x") }
    }

    @Test
    fun overridesDefaultToNothing() = runTest(dispatcher) {
        assertEquals(ModelOverrides(), Fixture(this).settings.overridesNow("m.gguf"))
    }

    @Test
    fun overridesAreStoredPerModel() = runTest(dispatcher) {
        val f = Fixture(this)
        val overrides = ModelOverrides(BackendPreference.CPU, 2, 8192, 0.3f)

        f.settings.setOverrides("a.gguf", overrides)

        assertEquals(overrides, f.settings.overridesNow("a.gguf"))
        assertEquals(ModelOverrides(), f.settings.overridesNow("b.gguf"))
        f.settings.setOverrides("a.gguf", ModelOverrides())
        assertEquals(ModelOverrides(), f.settings.overridesNow("a.gguf"))
    }

    @Test
    fun storedGarbageMeansDefault() = runTest(dispatcher) {
        val f = Fixture(this)
        f.dataStore.edit {
            it[stringPreferencesKey("model.a.gguf.backend")] = "TPU"
            it[intPreferencesKey("model.a.gguf.threads")] = 0
            it[intPreferencesKey("model.a.gguf.context")] = 5
            it[floatPreferencesKey("model.a.gguf.temperature")] = 9f
        }

        assertEquals(ModelOverrides(), f.settings.overridesNow("a.gguf"))
    }

    @Test
    fun invalidOverridesAreCallerBugs() = runTest(dispatcher) {
        val f = Fixture(this)
        assertThrows<IllegalArgumentException> { f.settings.setOverrides("a", ModelOverrides(threads = 0)) }
        assertThrows<IllegalArgumentException> { f.settings.setOverrides("a", ModelOverrides(contextLength = 100)) }
        assertThrows<IllegalArgumentException> { f.settings.setOverrides("a", ModelOverrides(temperature = 0.5f)) }
    }

    @Test
    fun overridesWinOverDefaults() {
        val defaults = InferenceDefaults(BackendPreference.AUTO, 4, 4096)
        assertEquals(LoadConfig(contextLength = 8192, threads = 4, backend = BackendPreference.CPU), ModelOverrides(backend = BackendPreference.CPU, contextLength = 8192).loadConfig(defaults))
        assertEquals(0.8f, ModelOverrides().temperature())
        assertEquals(0.3f, ModelOverrides(temperature = 0.3f).temperature())
    }

    @Test
    fun chooseAppliesSettingsAndRemembersTheModel() = runTest(dispatcher) {
        val f = Fixture(this)
        val file = f.model("m.gguf")
        f.inference.setContextLength(8192)
        f.settings.setOverrides("m.gguf", ModelOverrides(backend = BackendPreference.CPU, temperature = 0.3f))

        f.selection.choose(file)

        assertEquals(LoadConfig(contextLength = 8192, backend = BackendPreference.CPU).copy(threads = engine.loads.single().second.threads), engine.loads.single().second)
        assertEquals("m.gguf", f.settings.activeModelName.first())
        assertEquals(0.3f, f.selection.activeTemperature())
    }

    @Test
    fun failedChoiceIsNotRemembered() = runTest(dispatcher) {
        val f = Fixture(this)
        engine.loadResult = LoadResult.Failed(LoadError.MODEL_LOAD_FAILED)

        f.selection.choose(f.model("m.gguf"))

        assertNull(f.settings.activeModelName.first())
        assertEquals(0.8f, f.selection.activeTemperature())
    }

    @Test
    fun restoreLoadsTheRememberedModelIfItStillExists() = runTest(dispatcher) {
        val f = Fixture(this)
        f.model("m.gguf")
        f.settings.setActiveModel("m.gguf")

        f.selection.restore()

        assertEquals(File(f.modelsDir, "m.gguf"), engine.loads.single().first)
        f.settings.setActiveModel("gone.gguf")
        f.selection.restore()
        assertEquals(1, engine.loads.size)
    }

    @Test
    fun restoreWithNothingRememberedLoadsNothing() = runTest(dispatcher) {
        Fixture(this).selection.restore()
        assertEquals(0, engine.loads.size)
    }

    @Test
    fun deletingTheActiveModelUnloadsAndForgetsIt() = runTest(dispatcher) {
        val f = Fixture(this)
        val file = f.model("m.gguf")
        engine.loadResult = LoadResult.Loaded(ModelInfo("m", 1, 1, 1), Backend.CPU)
        f.selection.choose(file)
        f.settings.setOverrides("m.gguf", ModelOverrides(threads = 2))

        f.selection.delete(file)

        assertFalse(file.exists())
        assertEquals(1, engine.unloads)
        assertNull(f.settings.activeModelName.first())
        assertEquals(ModelOverrides(), f.settings.overridesNow("m.gguf"))
        assertEquals(emptyList<File>(), f.activeModel.state.value.installed)
    }

    @Test
    fun deletingAnotherModelKeepsTheActiveOne() = runTest(dispatcher) {
        val f = Fixture(this)
        val active = f.model("a.gguf")
        val other = f.model("b.gguf")
        f.selection.choose(active)

        f.selection.delete(other)

        assertEquals(0, engine.unloads)
        assertEquals("a.gguf", f.settings.activeModelName.first())
    }
}
