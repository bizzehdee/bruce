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
    /** `tokenizer.chat_template`, if at most 64 KB; untrusted text. */
    val chatTemplate: String? = null,
    /** Sliding-window attention: the window in tokens, and per layer whether it uses the window (true) or the whole context. */
    val slidingWindow: Long? = null,
    val slidingWindowPattern: List<Boolean>? = null,
    /** Head sizes of the sliding-window layers, when they differ from [keyLength] and [valueLength]. */
    val keyLengthSwa: Long? = null,
    val valueLengthSwa: Long? = null,
    /** The last this many layers reuse earlier layers' KV cache and keep none of their own (Gemma 3n and 4). */
    val sharedKvLayers: Long? = null,
    /** Bytes of tensors only ever looked up by row, which llama.cpp reads from the mapped file as needed rather than holding in memory. */
    val lookupOnlyBytes: Long = 0,
)

sealed interface GgufReadResult {
    data class Read(val metadata: GgufMetadata) : GgufReadResult

    data class Failed(val error: GgufError) : GgufReadResult
}

/** Result of parsing the first bytes of a GGUF file whose total size is known. */
sealed interface GgufPrefixResult {
    data class Read(val metadata: GgufMetadata) : GgufPrefixResult

    /** The header continues past the bytes supplied; retry with a longer prefix. */
    data object NeedMoreBytes : GgufPrefixResult

    data class Failed(val error: GgufError) : GgufPrefixResult
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
    private const val MAX_PATTERN_LAYERS = 4096
    private const val DEFAULT_ALIGNMENT = 32L

    /**
     * Per-layer token embeddings (Gemma 3n and 4): a lookup table that is 44% of Gemma 4 E4B's file.
     * Measured 2026-09-29: llama.cpp keeps it in the mapped file (CPU_Mapped, not repacked), and the
     * process's own memory for that model was 2.1 GB against a 5.3 GB file.
     */
    private val LOOKUP_ONLY_TENSORS = setOf("per_layer_token_embd.weight")

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
    private const val KEY_CHAT_TEMPLATE = "tokenizer.chat_template"
    private const val KEY_FILE_TYPE = "general.file_type"
    private const val KEY_ALIGNMENT = "general.alignment"
    private const val SWA_PATTERN_SUFFIX = ".attention.sliding_window_pattern"
    private const val MAX_TENSOR_NAME_BYTES = 256
    private const val CONTEXT_LENGTH_SUFFIX = ".context_length"

    /** Raised only inside the parser, and converted to [GgufError.MALFORMED]. */
    private class MalformedGguf : Exception()

    /** Raised only inside the parser when the supplied bytes end before the header does. */
    private class PrefixExhausted : Exception()

    fun read(file: File): GgufReadResult {
        if (!file.isFile) return GgufReadResult.Failed(GgufError.FILE_NOT_FOUND)
        return BufferedInputStream(file.inputStream()).use { stream ->
            when (val result = readPrefix(stream, file.length())) {
                is GgufPrefixResult.Read -> GgufReadResult.Read(result.metadata)
                is GgufPrefixResult.Failed -> GgufReadResult.Failed(result.error)
                // A whole file cannot end early unless it shrank while being read.
                GgufPrefixResult.NeedMoreBytes -> GgufReadResult.Failed(GgufError.MALFORMED)
            }
        }
    }

    /**
     * Parses GGUF metadata from [prefix], the first bytes of a file of [totalSize] bytes, such
     * as a partial download. Counts and lengths are still checked against [totalSize].
     */
    fun readPrefix(prefix: InputStream, totalSize: Long): GgufPrefixResult {
        val header = Gguf.readHeader(prefix)
        if (header.size < Gguf.magicLength) {
            return if (totalSize >= Gguf.magicLength) GgufPrefixResult.NeedMoreBytes else GgufPrefixResult.Failed(GgufError.NOT_GGUF)
        }
        if (!Gguf.hasMagic(header)) return GgufPrefixResult.Failed(GgufError.NOT_GGUF)
        val input = LittleEndianInput(prefix, totalSize, Gguf.magicLength.toLong())
        return try {
            when (val result = parse(input, totalSize)) {
                is GgufReadResult.Read -> GgufPrefixResult.Read(result.metadata)
                is GgufReadResult.Failed -> GgufPrefixResult.Failed(result.error)
            }
        } catch (e: MalformedGguf) {
            GgufPrefixResult.Failed(GgufError.MALFORMED)
        } catch (e: PrefixExhausted) {
            GgufPrefixResult.NeedMoreBytes
        }
    }

    private fun parse(input: LittleEndianInput, fileSize: Long): GgufReadResult {
        val version = input.int32()
        if (version !in SUPPORTED_VERSIONS) return GgufReadResult.Failed(GgufError.UNSUPPORTED_VERSION)
        val tensorCount = input.count()
        val keyValueCount = input.count()

        val strings = mutableMapOf<String, String>()
        val integers = mutableMapOf<String, Long>()
        var pattern: List<Boolean>? = null
        repeatCount(keyValueCount) {
            val key = input.string(MAX_KEPT_STRING_BYTES)
            when (val type = input.int32()) {
                TYPE_STRING ->
                    when (key) {
                        KEY_ARCHITECTURE, KEY_NAME -> strings[key] = input.string(MAX_KEPT_STRING_BYTES)
                        // Kept only if it is of a size worth judging; a longer one is skipped, not an error.
                        KEY_CHAT_TEMPLATE -> input.stringOrSkip(MAX_KEPT_STRING_BYTES)?.let { strings[key] = it }
                        else -> input.skipString()
                    }
                TYPE_ARRAY -> if (key.endsWith(SWA_PATTERN_SUFFIX)) pattern = boolArrayOrSkip(input) else skipArray(input)
                TYPE_UINT32 -> integers[key] = input.uint32()
                TYPE_INT32 -> integers[key] = input.int32().toLong()
                TYPE_UINT64, TYPE_INT64 -> integers[key] = input.int64()
                else -> input.skip(scalarSize(type))
            }
        }

        var parameterCount = 0L
        val offsets = mutableListOf<Long>()
        val lookupOffsets = mutableListOf<Long>()
        repeatCount(tensorCount) {
            val name = input.stringOrSkip(MAX_TENSOR_NAME_BYTES)
            val dimensions = input.int32()
            if (dimensions !in 1..MAX_TENSOR_DIMENSIONS) throw MalformedGguf()
            var elements = 1L
            repeat(dimensions) {
                val size = input.int64()
                if (size < 0) throw MalformedGguf()
                elements = checkedMultiply(elements, size)
            }
            input.skip(Int.SIZE_BYTES.toLong())
            val offset = input.int64()
            offsets += offset
            if (name in LOOKUP_ONLY_TENSORS) lookupOffsets += offset
            parameterCount = checkedAdd(parameterCount, elements)
        }
        val alignment = integers[KEY_ALIGNMENT]?.takeIf { it > 0 } ?: DEFAULT_ALIGNMENT
        val dataStart = (input.position + alignment - 1) / alignment * alignment
        val lookupOnlyBytes = lookupOffsets.sumOf { tensorBytes(it, offsets, fileSize - dataStart) }

        val architecture = strings[KEY_ARCHITECTURE]
        val fileType = integers[KEY_FILE_TYPE]?.toInt()
        return GgufReadResult.Read(
            GgufMetadata(
                version = version,
                architecture = architecture,
                name = strings[KEY_NAME],
                chatTemplate = strings[KEY_CHAT_TEMPLATE],
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
                slidingWindow = architecture?.let { integers["$it.attention.sliding_window"] },
                slidingWindowPattern = pattern,
                keyLengthSwa = architecture?.let { integers["$it.attention.key_length_swa"] },
                valueLengthSwa = architecture?.let { integers["$it.attention.value_length_swa"] },
                sharedKvLayers = architecture?.let { integers["$it.attention.shared_kv_layers"] },
                lookupOnlyBytes = lookupOnlyBytes,
            ),
        )
    }

    /** A tensor's size: up to the next tensor's data, or to the end of the file for the last. */
    private fun tensorBytes(offset: Long, offsets: List<Long>, dataBytes: Long): Long {
        val next = offsets.filter { it > offset }.minOrNull() ?: dataBytes
        return (next - offset).coerceAtLeast(0)
    }

    private fun boolArrayOrSkip(input: LittleEndianInput): List<Boolean>? {
        val elementType = input.int32()
        val count = input.count()
        if (elementType != TYPE_BOOL || count > MAX_PATTERN_LAYERS) {
            when (elementType) {
                TYPE_STRING -> repeatCount(count) { input.skipString() }
                TYPE_ARRAY -> throw MalformedGguf()
                else -> input.skip(checkedMultiply(count, scalarSize(elementType)))
            }
            return null
        }
        return List(count.toInt()) { input.byte() != 0 }
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
        start: Long,
    ) {
        var position: Long = start
            private set

        fun byte(): Int = bytes(1)[0].toInt() and 0xFF

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

        /** The string, or null (skipped) if it is longer than [maxBytes]. */
        fun stringOrSkip(maxBytes: Int): String? {
            val length = int64()
            if (length < 0) throw MalformedGguf()
            if (length > maxBytes) {
                skip(length)
                return null
            }
            return String(bytes(length.toInt()), Charsets.UTF_8)
        }

        fun skip(count: Long) {
            if (count < 0 || count > remaining) throw MalformedGguf()
            var left = count
            while (left > 0) {
                val skipped = stream.skip(left)
                if (skipped <= 0) {
                    if (stream.read() < 0) throw PrefixExhausted()
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
                if (read < 0) throw PrefixExhausted()
                filled += read
            }
            position += count
            return buffer
        }
    }
}
