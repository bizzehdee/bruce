package com.bizzeh.bruce.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SavedPromptsTest {
    @TempDir
    lateinit var root: File

    private val dir get() = File(root, "prompt-states")
    private fun model(name: String) = File(root, name).apply { writeBytes(ByteArray(8)) }

    @Test
    fun anythingTheStateDependsOnGivesAnotherFile() {
        val saved = SavedPrompts(dir, "build-1")
        val model = model("a.gguf")
        val first = saved.fileFor(model, 4096, "system")

        assertEquals(first, saved.fileFor(model, 4096, "system"))
        assertNotEquals(first, saved.fileFor(model, 4096, "other system"))
        assertNotEquals(first, saved.fileFor(model, 2048, "system"))
        assertNotEquals(first, SavedPrompts(dir, "build-2").fileFor(model, 4096, "system"))
        model.writeBytes(ByteArray(9))
        assertNotEquals(first, saved.fileFor(model, 4096, "system"))
    }

    @Test
    fun aModelKeepsOnlyItsNewestStateAndOtherModelsAreLeftAlone() {
        val saved = SavedPrompts(dir, "build")
        val a = model("a.gguf")
        val b = model("b.gguf")
        val old = saved.fileFor(a, 4096, "old").apply { writeText("state") }
        val other = saved.fileFor(b, 4096, "old").apply { writeText("state") }

        val new = saved.fileFor(a, 4096, "new")

        assertFalse(old.exists(), "the model's earlier state is deleted")
        assertTrue(other.exists())
        assertEquals(dir, new.parentFile)
    }

    @Test
    fun forgettingAModelDeletesItsState() {
        val saved = SavedPrompts(dir, "build")
        val a = model("a.gguf")
        val b = model("b.gguf")
        val state = saved.fileFor(a, 4096, "system").apply { writeText("state") }
        val other = saved.fileFor(b, 4096, "system").apply { writeText("state") }

        saved.forget(a)

        assertFalse(state.exists())
        assertTrue(other.exists())
    }
}
