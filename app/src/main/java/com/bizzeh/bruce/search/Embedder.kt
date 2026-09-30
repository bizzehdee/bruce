package com.bizzeh.bruce.search

import com.bizzeh.bruce.hardware.CpuTopology
import com.bizzeh.bruce.inference.LlamaApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Text to vectors for file search (TASK-096); LlamaEmbedder on a phone. */
interface Embedder {
    /** The vector for a passage of a document, or null if it cannot be made. */
    suspend fun passage(text: String): FloatArray?

    /** The vector for a question, in the same space as passages. */
    suspend fun query(text: String): FloatArray?

    /** Frees the model's memory; the next use loads it again. */
    suspend fun release()
}

/**
 * The chosen embedding model (research/experiments/embedding-models-2026-09-30): EmbeddingGemma-300M
 * from ggml-org, downloaded only when the user sets up file search. Its prompts are the model's own.
 */
object EmbeddingModel {
    const val REPOSITORY = "ggml-org/embeddinggemma-300M-GGUF"
    const val PATH = "embeddinggemma-300M-Q8_0.gguf"
    const val SIZE_BYTES = 333_590_944L
    const val SHA256 = "b5ce9d77a3fc4b3b39ccb5643c36777911cc4eb46a66962eadfa3f5f60490d63"
    const val TERMS_URL = "https://ai.google.dev/gemma/terms"
    const val DIR = "embeddings"
    const val QUERY_PREFIX = "task: search result | query: "
    const val PASSAGE_PREFIX = "title: none | text: "

    /** Tokens per passage; chunks are sized to fit (Chunker). */
    const val CONTEXT = 1024

    fun file(modelsDir: File) = File(File(modelsDir, DIR), PATH)
}

/**
 * Loads the model on first use, on its own context apart from the chat's, and keeps it until
 * [release]; about 0.8 GB on the Xperia 1 II, so the indexer releases it when it is done.
 */
internal class LlamaEmbedder(
    private val llama: LlamaApi,
    private val model: File,
    private val dispatcher: CoroutineDispatcher,
    private val threads: Int = CpuTopology.performanceCoreCount(),
) : Embedder {
    private val mutex = Mutex()
    private var handle = 0L

    override suspend fun passage(text: String) = embed(EmbeddingModel.PASSAGE_PREFIX + text)

    override suspend fun query(text: String) = embed(EmbeddingModel.QUERY_PREFIX + text)

    private suspend fun embed(text: String): FloatArray? = mutex.withLock {
        withContext(dispatcher) {
            if (handle == 0L) {
                if (!model.isFile) return@withContext null
                handle = llama.loadEmbedder(model.absolutePath, EmbeddingModel.CONTEXT, threads)
            }
            if (handle == 0L) null else llama.embed(handle, text.toByteArray(Charsets.UTF_8))
        }
    }

    override suspend fun release() = mutex.withLock {
        withContext(dispatcher) {
            if (handle != 0L) llama.freeEmbedder(handle)
            handle = 0L
        }
    }
}
