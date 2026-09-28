package com.bizzeh.bruce.gguf

import com.bizzeh.bruce.gguf.GgufBuilder.Companion.writeInt
import com.bizzeh.bruce.gguf.GgufBuilder.Companion.writeLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class GgufReaderTest {
    @TempDir
    lateinit var dir: File

    private fun read(bytes: ByteArray) = GgufReader.read(File(dir, "m.gguf").apply { writeBytes(bytes) })

    private fun metadata(bytes: ByteArray) = (read(bytes) as GgufReadResult.Read).metadata

    private fun failure(bytes: ByteArray) = (read(bytes) as GgufReadResult.Failed).error

    @Test
    fun readsRealModelFixture() {
        val fixture = File("src/androidTest/assets/stories260K.gguf")

        val metadata = (GgufReader.read(fixture) as GgufReadResult.Read).metadata

        assertEquals(
            GgufMetadata(
                version = 3,
                architecture = "llama",
                name = "llama",
                parameterCount = 292_800L,
                tensorCount = 48L,
                contextLength = 2048L,
                fileType = null,
                quantisation = null,
                fileSizeBytes = fixture.length(),
                blockCount = 5L,
                embeddingLength = 64L,
                headCount = 8L,
                headCountKv = 4L,
            ),
            metadata,
        )
    }

    @Test
    fun prefixOfWholeFileReadsTheSameMetadata() {
        val fixture = File("src/androidTest/assets/stories260K.gguf")
        val full = (GgufReader.read(fixture) as GgufReadResult.Read).metadata

        val prefix = GgufReader.readPrefix(fixture.readBytes().inputStream(), fixture.length())

        assertEquals(GgufPrefixResult.Read(full), prefix)
    }

    @Test
    fun shortPrefixNeedsMoreBytes() {
        val fixture = File("src/androidTest/assets/stories260K.gguf")
        val bytes = fixture.readBytes()

        for (length in listOf(0, 3, 20, 1024)) {
            assertEquals(
                GgufPrefixResult.NeedMoreBytes,
                GgufReader.readPrefix(bytes.copyOf(length).inputStream(), fixture.length()),
                "prefix $length",
            )
        }
    }

    @Test
    fun prefixChecksMagicAndTinyFiles() {
        assertEquals(GgufPrefixResult.Failed(GgufError.NOT_GGUF), GgufReader.readPrefix("PK\u0003\u0004".byteInputStream(), 1_000))
        assertEquals(GgufPrefixResult.Failed(GgufError.NOT_GGUF), GgufReader.readPrefix("GG".byteInputStream(), 2))
    }

    @Test
    fun prefixStillRejectsCountsLargerThanTheWholeFile() {
        val bytes = GgufBuilder().build(keyValueCountOverride = 1_000_000L)

        assertEquals(GgufPrefixResult.Failed(GgufError.MALFORMED), GgufReader.readPrefix(bytes.inputStream(), bytes.size.toLong()))
    }

    @Test
    fun readsDeclaredQuantisationAndArchitectureSpecificContext() {
        val bytes = GgufBuilder()
            .string("general.architecture", "qwen3")
            .uint32("general.file_type", 15)
            .uint64("qwen3.context_length", 40_960L)
            .uint32("llama.context_length", 1)
            .tensor("token_embd.weight", 1024, 151_936)
            .tensor("output_norm.weight", 1024)
            .build()

        val metadata = metadata(bytes)

        assertEquals("qwen3", metadata.architecture)
        assertEquals("Q4_K_M", metadata.quantisation)
        assertEquals(15, metadata.fileType)
        assertEquals(40_960L, metadata.contextLength)
        assertEquals(1024L * 151_936 + 1024, metadata.parameterCount)
        assertEquals(2L, metadata.tensorCount)
        assertEquals(bytes.size.toLong(), metadata.fileSizeBytes)
    }

    @Test
    fun readsExplicitKeyAndValueLengths() {
        val metadata = metadata(
            GgufBuilder()
                .string("general.architecture", "gemma3")
                .uint32("gemma3.attention.key_length", 256)
                .uint32("gemma3.attention.value_length", 128)
                .build(),
        )

        assertEquals(256L, metadata.keyLength)
        assertEquals(128L, metadata.valueLength)
    }

    @Test
    fun unknownFileTypeKeepsNumberWithoutName() {
        val metadata = metadata(GgufBuilder().int32("general.file_type", 999).build())

        assertEquals(999, metadata.fileType)
        assertNull(metadata.quantisation)
    }

    @Test
    fun skipsEveryValueType() {
        val bytes = GgufBuilder()
            .scalar("u8", 0, 1).scalar("i8", 1, 1).scalar("u16", 2, 2).scalar("i16", 3, 2)
            .scalar("f32", 6, 4).scalar("bool", 7, 1).scalar("f64", 12, 8)
            .int64("i64", 5L)
            .string("tokenizer.chat_template", "{{ messages }}")
            .stringArray("tokenizer.ggml.tokens", "<s>", "</s>", "hello")
            .scalarArray("tokenizer.ggml.scores", 6, count = 3, elementBytes = 4)
            .apply {
                val sizes = mapOf(0 to 1, 1 to 1, 2 to 2, 3 to 2, 4 to 4, 5 to 4, 7 to 1, 10 to 8, 11 to 8, 12 to 8)
                sizes.forEach { (type, bytes) -> scalarArray("array.$type", type, count = 2, elementBytes = bytes) }
            }
            .string("general.architecture", "llama")
            .build()

        assertEquals("llama", metadata(bytes).architecture)
    }

    @Test
    fun contextLengthIsNullWithoutArchitecture() {
        assertNull(metadata(GgufBuilder().uint32("llama.context_length", 2048).build()).contextLength)
    }

    @Test
    fun acceptsVersionTwo() {
        assertEquals(2, metadata(GgufBuilder(version = 2).build()).version)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 4])
    fun rejectsUnsupportedVersions(version: Int) {
        assertEquals(GgufError.UNSUPPORTED_VERSION, failure(GgufBuilder(version = version).build()))
    }

    @Test
    fun missingFile() {
        assertEquals(GgufReadResult.Failed(GgufError.FILE_NOT_FOUND), GgufReader.read(File(dir, "absent.gguf")))
    }

    @Test
    fun notGguf() {
        assertEquals(GgufError.NOT_GGUF, failure("PK\u0003\u0004".toByteArray()))
    }

    @Test
    fun truncatedHeaderIsMalformed() {
        val bytes = GgufBuilder().string("general.architecture", "llama").tensor("t", 4, 4).build()

        for (length in listOf(6, 12, 30, bytes.size - 1)) {
            assertEquals(GgufError.MALFORMED, failure(bytes.copyOf(length)), "length $length")
        }
    }

    @Test
    fun countLargerThanFileIsMalformed() {
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().build(keyValueCountOverride = 1_000_000L)))
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().build(tensorCountOverride = -1L)))
    }

    @Test
    fun hugeStringLengthIsRejectedWithoutAllocating() {
        val bytes = GgufBuilder().rawKeyValue("general.architecture", 8) { writeLong(Long.MAX_VALUE) }.build()

        assertEquals(GgufError.MALFORMED, failure(bytes))
    }

    @Test
    fun oversizedKeptStringIsMalformed() {
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().string("general.name", "x".repeat(70_000)).build()))
    }

    @Test
    fun skippedStringLongerThanFileIsMalformed() {
        val bytes = GgufBuilder().rawKeyValue("tokenizer.chat_template", 8) { writeLong(1_000_000L) }.build()

        assertEquals(GgufError.MALFORMED, failure(bytes))
    }

    @Test
    fun unknownValueTypeIsMalformed() {
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().rawKeyValue("k", 99) {}.build()))
    }

    @Test
    fun nestedArrayIsMalformed() {
        val bytes = GgufBuilder().rawKeyValue("k", 9) {
            writeInt(9)
            writeLong(1)
            writeInt(4)
            writeLong(1)
            writeInt(0)
        }.build()

        assertEquals(GgufError.MALFORMED, failure(bytes))
    }

    @Test
    fun negativeStringLengthsAreMalformed() {
        val kept = GgufBuilder().rawKeyValue("general.name", 8) { writeLong(-1) }.build()
        val skipped = GgufBuilder().rawKeyValue("tokenizer.chat_template", 8) { writeLong(-1) }.build()

        assertEquals(GgufError.MALFORMED, failure(kept + ByteArray(16)))
        assertEquals(GgufError.MALFORMED, failure(skipped + ByteArray(16)))
    }

    @Test
    fun arrayWhoseSizeOverflowsIsMalformed() {
        val bytes = GgufBuilder().rawKeyValue("k", 9) {
            writeInt(12)
            writeLong(Long.MAX_VALUE / 4)
        }.build()

        assertEquals(GgufError.MALFORMED, failure(bytes))
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 5])
    fun tensorDimensionCountOutOfRangeIsMalformed(dimensions: Int) {
        val bytes = GgufBuilder().build(tensorCountOverride = 1) + tensorHeader(dimensions)

        assertEquals(GgufError.MALFORMED, failure(bytes))
    }

    @Test
    fun negativeTensorDimensionIsMalformed() {
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().tensor("t", 4, -1).build()))
    }

    @Test
    fun parameterCountOverflowIsMalformed() {
        val huge = Long.MAX_VALUE / 2
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().tensor("t", huge, 4).build()))
        assertEquals(GgufError.MALFORMED, failure(GgufBuilder().tensor("a", huge).tensor("b", huge).tensor("c", huge).build()))
    }

    private fun tensorHeader(dimensions: Int): ByteArray = java.io.ByteArrayOutputStream().apply {
        writeLong(1)
        write('t'.code)
        writeInt(dimensions)
        repeat(dimensions) { writeLong(2) }
        writeInt(0)
        writeLong(0)
    }.toByteArray()
}
