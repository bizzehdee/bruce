package com.bizzeh.bruce.inference

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LlamaCppEngineVisionTest {
    @TempDir
    lateinit var dir: File

    private val llama = FakeLlamaApi()
    private val dispatcher = StandardTestDispatcher()
    private val engine = LlamaCppEngine(llama, dispatcher)
    private val image = RgbImage(2, 1, ByteArray(6))

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private suspend fun load(projector: String? = "/models/projectors/m.gguf") {
        val file = File(dir, "m.gguf").apply { writeBytes("GGUF".toByteArray() + ByteArray(16)) }
        engine.loadModel(file, LoadConfig(threads = 3, projectorPath = projector))
    }

    @Test
    fun aProjectorIsLoadedWithTheModelAndFreedWithIt() = test {
        load()

        assertEquals(listOf("/models/projectors/m.gguf" to 3), llama.projectorRequests)
        assertTrue(engine.getModelInfo()!!.vision)
        assertEquals("<__media__>", engine.mediaMarker())
        assertEquals(64, engine.imageTokens(image))

        engine.unloadModel()
        assertEquals(listOf(40L), llama.freedProjectors)
        assertNull(engine.mediaMarker())
    }

    @Test
    fun withoutAProjectorTheModelStillLoadsButCannotSee() = test {
        llama.projectorHandle = 0L
        load()
        assertFalse(engine.getModelInfo()!!.vision)
        assertNull(engine.mediaMarker())
        assertNull(engine.imageTokens(image))

        engine.unloadModel()
        assertEquals(emptyList<Long>(), llama.freedProjectors)
        load(projector = null)
        assertEquals(1, llama.projectorRequests.size)
    }

    @Test
    fun anImageTheProjectorCannotReadHasNoCount() = test {
        load()
        llama.imageTokenResult = -1

        assertNull(engine.imageTokens(image))
    }

    @Test
    fun promptsWithImagesGoThroughTheProjector() = test {
        load()
        llama.script("A", " moon")

        val events = engine.generate(GenerationRequest("<__media__>\nWhat is this?", images = listOf(image, RgbImage(1, 1, ByteArray(3))))).toList()

        assertEquals("A moon", events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text })
        assertArrayEquals("<__media__>\nWhat is this?".toByteArray(), llama.lastPrompt)
        assertEquals(listOf(Triple(2, 1, 6), Triple(1, 1, 3)), llama.lastImages)
    }

    @Test
    fun imagesWithoutAProjectorFail() = test {
        llama.projectorHandle = 0L
        load()

        val events = engine.generate(GenerationRequest("x", images = listOf(image))).toList()

        assertEquals(listOf(GenerationEvent.Failed(GenerationError.IMAGES_UNUSABLE)), events)
        assertNull(llama.lastImages)
    }

    @Test
    fun rgbImagesMustHoldThreeBytesAPixel() {
        assertThrows<IllegalArgumentException> { RgbImage(2, 2, ByteArray(11)) }
        assertThrows<IllegalArgumentException> { RgbImage(0, 2, ByteArray(0)) }
    }
}
