package com.bizzeh.bruce.models

import com.bizzeh.bruce.huggingface.HubFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectorsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun projectorsAreFoundByName() {
        assertTrue(Projectors.isProjector("sub/mmproj-model-f16.gguf"))
        assertTrue(Projectors.isProjector("gemma-3-4b-it-MMPROJ-Q8_0.gguf"))
        assertFalse(Projectors.isProjector("mmproj/gemma-3-4b-it-Q4_K_M.gguf"))
        assertFalse(SizeEstimate.isWholeModel("mmproj-F16.gguf"))
    }

    @Test
    fun f16IsPreferredThenTheSmallest() {
        val bf16 = HubFile("mmproj-BF16.gguf", 900, "a")
        val f16 = HubFile("mmproj-F16.gguf", 850, "b")
        val q8 = HubFile("mmproj-Q8_0.gguf", 500, "c")
        val model = HubFile("model-Q4_0.gguf", 100, "d")

        assertEquals(f16, Projectors.choose(listOf(model, bf16, q8, f16)))
        assertEquals(q8, Projectors.choose(listOf(model, bf16, q8)))
        assertNull(Projectors.choose(listOf(model)))
    }

    @Test
    fun aModelsProjectorSitsBesideItUnderItsName() {
        val model = File(dir, "gemma.gguf").apply { writeText("m") }
        assertNull(Projectors.installedFor(model))
        assertEquals(0, Projectors.bytesFor(model))

        Projectors.fileFor(model).apply { parentFile.mkdirs(); writeText("proj") }

        assertEquals(File(dir, "projectors/gemma.gguf"), Projectors.installedFor(model))
        assertEquals(4, Projectors.bytesFor(model))
    }
}
