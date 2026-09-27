package com.bizzeh.bruce

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.LlamaCppEngine
import com.bizzeh.bruce.models.ModelImporter
import com.bizzeh.bruce.prototype.PrototypeActions
import com.bizzeh.bruce.prototype.PrototypeScreen
import com.bizzeh.bruce.prototype.PrototypeViewModel
import com.bizzeh.bruce.ui.theme.BruceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val viewModel: PrototypeViewModel by viewModels { factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BruceTheme {
                val state by viewModel.state.collectAsState()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(viewModel::import)
                }
                val actions = remember { actions { picker.launch(arrayOf("*/*")) } }
                PrototypeScreen(state, actions)
            }
        }
    }

    private fun actions(pickModel: () -> Unit) = object : PrototypeActions {
        override fun importModel() = pickModel()
        override fun select(file: File) = viewModel.select(file)
        override fun setBackend(backend: BackendPreference) = viewModel.setBackend(backend)
        override fun load() = viewModel.load()
        override fun setPrompt(prompt: String) = viewModel.setPrompt(prompt)
        override fun generate() = viewModel.generate()
        override fun stop() = viewModel.stop()
    }

    private fun factory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val engine = LlamaCppEngine.create(applicationInfo.nativeLibraryDir, nativeThread)
            val modelsDir = File(filesDir, MODELS_DIR)
            val importer = ModelImporter(contentResolver, modelsDir, Dispatchers.IO)
            PrototypeViewModel(
                engine = engine,
                modelsDir = modelsDir,
                importModel = importer::import,
                detectCpuFeatures = CpuFeatures::detect,
                memoryInfo = {
                    ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
                },
                ioDispatcher = Dispatchers.IO,
                release = {
                    // onCleared is not a coroutine; freeing native memory is quick and must finish
                    // before the dispatcher's thread is closed.
                    runBlocking { engine.unloadModel() }
                    nativeThread.close()
                },
            )
        }
    }

    companion object {
        const val MODELS_DIR = "models"
    }
}
