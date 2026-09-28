package com.bizzeh.bruce.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LlamaArchitecturesTest {
    @Test
    fun listMatchesTheBundledLlamaCpp() {
        val source = File("src/main/cpp/llama.cpp/src/llama-arch.cpp").readText()
        val table = source.substring(source.indexOf("LLM_ARCH_NAMES"), source.indexOf("};", source.indexOf("LLM_ARCH_NAMES")))
        val names = Regex("""\{\s*LLM_ARCH_\w+\s*,\s*"([^"]+)"\s*\}""").findAll(table).map { it.groupValues[1] }.toSet()

        assertEquals(names, LlamaArchitectures.all, "llama.cpp changed its architecture table; update LlamaArchitectures")
    }

    @Test
    fun chatSupport() {
        assertTrue(LlamaArchitectures.supportsChat("qwen3"))
        assertTrue(LlamaArchitectures.supportsChat("llama"))
        assertFalse(LlamaArchitectures.supportsChat("clip"))
        assertFalse(LlamaArchitectures.supportsChat("not-a-real-architecture"))
    }
}
