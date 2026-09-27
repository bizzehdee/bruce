package com.bizzeh.bruce.gguf

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

data class GgufMetadata(
    val version: Int,
    val architecture: String?,
    val name: String?,
    val parameterCount: Long,
    val tensorCount: Long,
    val contextLength: Long?,
    /** `general.file_type`, a llama_ftype value; null when the file does not declare it. */
    val fileType: Int?,
    /** Quantisation name for [fileType], e.g. "Q4_K_M"; null when undeclared or unknown. */
    val quantisation: String?,
    val fileSizeBytes: Long,
    /** Transformer shape, from `<architecture>.*` keys; null when the file does not declare it. */
    val blockCount: Long? = null,
    val embeddingLength: Long? = null,
    val headCount: Long? = null,
    /** Defaults to [headCount] in llama.cpp when absent. */
    val headCountKv: Long? = null,
    val keyLength: Long? = null,
    val valueLength: Long? = null,
)

sealed interface GgufReadResult {
    data class Read(val metadata: GgufMetadata) : GgufReadResult

    data class Failed(val error: GgufError) : GgufReadResult
}

enum class GgufError {
    FILE_NOT_FOUND,
    NOT_GGUF,
    UNSUPPORTED_VERSION,
    MALFORMED,
}

/**
 * Reads GGUF header metadata without reading tensor data. The file is untrusted:
 * every count and length is checked against the bytes remaining before it is used.
 */
object GgufReader {
    private val SUPPORTED_VERSIONS = 2..3
    private const val MAX_KEPT_STRING_BYTES = 64 * 1024
    private const val MAX_TENSOR_DIMENSIONS = 4

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    private const val KEY_ARCHITECTURE = "general.architecture"
    private const val KEY_NAME = "general.name"
    private const val KEY_FILE_TYPE = "general.file_type"
    private const val CONTEXT_LENGTH_SUFFIX = ".context_length"

    /** Raised only inside the parser, and converted to [GgufError.MALFORMED] by [read]. */
    private class MalformedGguf : Exception()

    fun read(file: File): GgufReadResult {
        if (!file.isFile) return GgufReadResult.Failed(GgufError.FILE_NOT_FOUND)
        val fileSize = file.length()
        return BufferedInputStream(file.inputStream()).use { stream ->
            if (!Gguf.hasMagic(Gguf.readHeader(stream))) return GgufReadResult.Failed(GgufError.NOT_GGUF)
            val input = LittleEndianInput(stream, fileSize, Gguf.magicLength.toLong())
            try {
                parse(input, fileSize)
            } catch (e: MalformedGguf) {
                GgufReadResult.Failed(GgufError.MALFORMED)
            }
        }
    }

    private fun parse(input: LittleEndianInput, fileSize: Long): GgufReadResult {
        val version = input.int32()
        if (version !in SUPPORTED_VERSIONS) return GgufReadResult.Failed(GgufError.UNSUPPORTED_VERSION)
        val tensorCount = input.count()
        val keyValueCount = input.count()

        val strings = mutableMapOf<String, String>()
        val integers = mutableMapOf<String, Long>()
        repeatCount(keyValueCount) {
            val key = input.string(MAX_KEPT_STRING_BYTES)
            when (val type = input.int32()) {
                TYPE_STRING ->
                    if (key == KEY_ARCHITECTURE || key == KEY_NAME) {
                        strings[key] = input.string(MAX_KEPT_STRING_BYTES)
                    } else {
                        input.skipString()
                    }
                TYPE_ARRAY -> skipArray(input)
                TYPE_UINT32 -> integers[key] = input.uint32()
                TYPE_INT32 -> integers[key] = input.int32().toLong()
                TYPE_UINT64, TYPE_INT64 -> integers[key] = input.int64()
                else -> input.skip(scalarSize(type))
            }
        }

        var parameterCount = 0L
        repeatCount(tensorCount) {
            input.skipString()
            val dimensions = input.int32()
            if (dimensions !in 1..MAX_TENSOR_DIMENSIONS) throw MalformedGguf()
            var elements = 1L
            repeat(dimensions) {
                val size = input.int64()
                if (size < 0) throw MalformedGguf()
                elements = checkedMultiply(elements, size)
            }
            input.skip(Int.SIZE_BYTES.toLong() + Long.SIZE_BYTES)
            parameterCount = checkedAdd(parameterCount, elements)
        }

        val architecture = strings[KEY_ARCHITECTURE]
        val fileType = integers[KEY_FILE_TYPE]?.toInt()
        return GgufReadResult.Read(
            GgufMetadata(
                version = version,
                architecture = architecture,
                name = strings[KEY_NAME],
                parameterCount = parameterCount,
                tensorCount = tensorCount,
                contextLength = architecture?.let { integers[it + CONTEXT_LENGTH_SUFFIX] },
                fileType = fileType,
                quantisation = fileType?.let(LlamaFileTypes::name),
                fileSizeBytes = fileSize,
                blockCount = architecture?.let { integers["$it.block_count"] },
                embeddingLength = architecture?.let { integers["$it.embedding_length"] },
                headCount = architecture?.let { integers["$it.attention.head_count"] },
                headCountKv = architecture?.let { integers["$it.attention.head_count_kv"] },
                keyLength = architecture?.let { integers["$it.attention.key_length"] },
                valueLength = architecture?.let { integers["$it.attention.value_length"] },
            ),
        )
    }

    private fun skipArray(input: LittleEndianInput) {
        val elementType = input.int32()
        val count = input.count()
        when (elementType) {
            TYPE_STRING -> repeatCount(count) { input.skipString() }
            TYPE_ARRAY -> throw MalformedGguf()
            else -> input.skip(checkedMultiply(count, scalarSize(elementType)))
        }
    }

    /** Counts come from a file that may exceed 2 GB, so they do not fit [repeat]'s Int. */
    private inline fun repeatCount(count: Long, action: () -> Unit) {
        var done = 0L
        while (done < count) {
            action()
            done++
        }
    }

    private fun scalarSize(type: Int): Long = when (type) {
        TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
        TYPE_UINT16, TYPE_INT16 -> 2
        TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
        TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
        else -> throw MalformedGguf()
    }

    private fun checkedMultiply(a: Long, b: Long): Long = try {
        Math.multiplyExact(a, b)
    } catch (e: ArithmeticException) {
        throw MalformedGguf()
    }

    private fun checkedAdd(a: Long, b: Long): Long = try {
        Math.addExact(a, b)
    } catch (e: ArithmeticException) {
        throw MalformedGguf()
    }

    private class LittleEndianInput(
        private val stream: InputStream,
        private val size: Long,
        private var position: Long,
    ) {
        private val remaining: Long get() = size - position

        fun int32(): Int {
            val b = bytes(Int.SIZE_BYTES)
            return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
        }

        fun uint32(): Long = int32().toLong() and 0xFFFF_FFFFL

        fun int64(): Long {
            val low = uint32()
            val high = uint32()
            return low or (high shl 32)
        }

        /** An element count: every element occupies at least one byte, so it cannot exceed what remains. */
        fun count(): Long {
            val count = int64()
            if (count < 0 || count > remaining) throw MalformedGguf()
            return count
        }

        fun string(maxBytes: Int): String {
            val length = int64()
            if (length < 0 || length > maxBytes) throw MalformedGguf()
            return String(bytes(length.toInt()), Charsets.UTF_8)
        }

        fun skipString() = skip(int64())

        fun skip(count: Long) {
            if (count < 0 || count > remaining) throw MalformedGguf()
            var left = count
            while (left > 0) {
                val skipped = stream.skip(left)
                if (skipped <= 0) {
                    if (stream.read() < 0) throw MalformedGguf()
                    left--
                } else {
                    left -= skipped
                }
            }
            position += count
        }

        private fun bytes(count: Int): ByteArray {
            if (count > remaining) throw MalformedGguf()
            val buffer = ByteArray(count)
            var filled = 0
            while (filled < count) {
                val read = stream.read(buffer, filled, count - filled)
                if (read < 0) throw MalformedGguf()
                filled += read
            }
            position += count
            return buffer
        }
    }
}
