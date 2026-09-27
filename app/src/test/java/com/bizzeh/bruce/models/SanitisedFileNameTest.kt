package com.bizzeh.bruce.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SanitisedFileNameTest {
    @ParameterizedTest
    @CsvSource(
        "qwen3-4b-q4km.gguf, qwen3-4b-q4km.gguf",
        "../../databases/bruce.db, bruce.db.gguf",
        "..\\evil.gguf, _evil.gguf",
        ".hidden.gguf, hidden.gguf",
        "Model Name (1).GGUF, Model_Name__1_.gguf",
        "no-extension, no-extension.gguf",
        "'', model.gguf",
        "..., model.gguf",
    )
    fun sanitises(displayName: String, expected: String) {
        assertEquals(expected, ModelImporter.sanitisedFileName(displayName))
    }

    @Test
    fun nullNameUsesDefault() {
        assertEquals("model.gguf", ModelImporter.sanitisedFileName(null))
    }

    @Test
    fun longNamesAreTruncated() {
        assertEquals("a".repeat(120) + ".gguf", ModelImporter.sanitisedFileName("a".repeat(500) + ".gguf"))
    }
}
