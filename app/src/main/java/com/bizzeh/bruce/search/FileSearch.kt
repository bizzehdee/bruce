package com.bizzeh.bruce.search

import android.util.Log
import com.bizzeh.bruce.policy.DocumentAccess
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the index holds and is doing, for Settings. */
data class IndexStatus(
    val files: Int = 0,
    val passages: Int = 0,
    val updating: Boolean = false,
    /** Files looked at so far in an update. */
    val checked: Int = 0,
    val lastUpdated: Long? = null,
)

/** A passage that matched a search, with the file it came from. */
data class Hit(val path: String, val text: String, val score: Float)

/**
 * Local file search (TASK-096): the text of the user's granted files, cut into passages
 * (Chunker), embedded on the phone (Embedder) and kept in `search.db`. Nothing leaves the phone.
 * An update embeds only files that are new or changed, drops those gone or no longer granted, and
 * frees the model's memory when done. [extract] gives a file's text from its name, type and bytes,
 * or null for a file that is not text (DocumentReader.parse).
 */
class FileSearch(
    private val dao: SearchDao,
    private val grants: suspend () -> List<Grant>,
    private val access: DocumentAccess,
    private val embedder: Embedder,
    private val extract: (name: String, type: String?, bytes: ByteArray) -> String?,
    private val chunker: Chunker = Chunker(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val state = MutableStateFlow(IndexStatus())
    val status: StateFlow<IndexStatus> = state.asStateFlow()

    suspend fun refreshCounts() {
        state.update { it.copy(files = dao.files().size, passages = dao.passageCount()) }
    }

    /** Brings the index in line with the granted files. */
    suspend fun update() {
        state.update { it.copy(updating = true, checked = 0) }
        try {
            val known = dao.files().associateBy { it.documentUri }
            val seen = mutableSetOf<String>()
            for (grant in grants().filter { it.available }) {
                for ((path, document) in files(grant)) {
                    val uri = document.uri.toString()
                    if (!seen.add(uri)) continue
                    state.update { it.copy(checked = it.checked + 1) }
                    val modified = access.lastModified(document.uri) ?: 0L
                    if (known[uri]?.let { it.modified == modified && modified != 0L && it.path == path } == true) continue
                    index(grant, path, document, modified)
                }
            }
            (known.keys - seen).forEach { dao.remove(it) }
        } finally {
            embedder.release()
            state.update { it.copy(updating = false, lastUpdated = clock()) }
            refreshCounts()
        }
    }

    private suspend fun index(grant: Grant, path: String, document: DocumentRef, modified: Long) {
        val bytes = access.read(document.uri, MAX_FILE_BYTES + 1)?.takeIf { it.size <= MAX_FILE_BYTES }
        val text = bytes?.let { extract(path.substringAfterLast('/'), access.mimeType(document.uri), it) }?.take(MAX_FILE_CHARS)
        val uri = document.uri.toString()
        if (text.isNullOrBlank()) return dao.remove(uri)
        val passages = chunker.chunks(text).take(MAX_PASSAGES_PER_FILE).mapIndexedNotNull { position, passage ->
            embedder.passage(passage)?.let { PassageEntity(documentUri = uri, position = position, text = passage, vector = Vectors.toBytes(it)) }
        }
        if (passages.isEmpty()) {
            Log.w(TAG, "a file gave no embeddings")
            return dao.remove(uri)
        }
        dao.replace(IndexedFileEntity(uri, grant.id, path, modified), passages)
    }

    /** Every file in a grant with its path, folders walked depth-first up to the limits. */
    private fun files(grant: Grant): List<Pair<String, DocumentRef>> {
        if (grant.kind == GrantKind.FILE) return listOf(grant.name to DocumentRef(grant.uri, isDirectory = false))
        val found = mutableListOf<Pair<String, DocumentRef>>()
        fun walk(folder: DocumentRef, path: String, depth: Int) {
            for ((name, child) in access.children(grant.uri, folder).sortedBy { it.first }) {
                if (found.size >= MAX_FILES) return
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth < MAX_DEPTH) walk(child, "$path/$name", depth + 1)
                } else {
                    found += "$path/$name" to child
                }
            }
        }
        walk(access.root(grant.uri), grant.name, 0)
        return found
    }

    /** The passages most like [query], best first; empty with no index or no model. */
    suspend fun search(query: String, limit: Int): List<Hit> {
        val passages = dao.passages()
        if (passages.isEmpty()) return emptyList()
        val vector = embedder.query(query) ?: return emptyList()
        val paths = dao.files().associate { it.documentUri to it.path }
        return passages.asSequence()
            .map { it to Vectors.similarity(vector, Vectors.fromBytes(it.vector)) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (passage, score) -> Hit(paths[passage.documentUri].orEmpty(), passage.text, score) }
            .toList()
    }

    suspend fun clear() {
        dao.deleteAllPassages()
        dao.deleteAllFiles()
        embedder.release()
        refreshCounts()
    }

    companion object {
        private const val TAG = "BruceSearch"
        const val MAX_FILES = 2000
        const val MAX_DEPTH = 8
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        /** About 100 passages; the start of a longer document is indexed. */
        const val MAX_FILE_CHARS = 100_000
        const val MAX_PASSAGES_PER_FILE = 100
    }
}
