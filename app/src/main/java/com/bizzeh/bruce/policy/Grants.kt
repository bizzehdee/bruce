package com.bizzeh.bruce.policy

import android.net.Uri
import com.bizzeh.bruce.skills.SkillRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class GrantKind { FILE, FOLDER }

/** A granted file or folder. [available] is false once Android no longer holds Bruce's access to it. */
data class Grant(val id: Long, val uri: Uri, val name: String, val kind: GrantKind, val grantedAt: Long, val available: Boolean)

/** A document inside a grant, as the provider reports it. */
data class DocumentRef(val uri: Uri, val isDirectory: Boolean)

/**
 * What Bruce needs from the Storage Access Framework. An interface so the grant and path rules can
 * be tested without a document provider.
 */
interface DocumentAccess {
    /** Keeps access across restarts; read and write where the picker allowed both. */
    fun take(uri: Uri)

    fun release(uri: Uri)

    /** URIs Android still holds persisted access to. */
    fun persisted(): Set<Uri>

    fun displayName(uri: Uri, kind: GrantKind): String?

    /** The folder a tree URI names. */
    fun root(tree: Uri): DocumentRef

    /** [folder]'s children with their display names, built from [tree] so the provider checks they belong to it. */
    fun children(tree: Uri, folder: DocumentRef): List<Pair<String, DocumentRef>>

    fun mimeType(document: Uri): String?

    /** Reads at most [maxBytes] of [document]; null if it cannot be opened. */
    fun read(document: Uri, maxBytes: Int): ByteArray?
}

/** The user's grants. Only the Permissions screen adds or revokes them; nothing the model reaches holds this store. */
class GrantStore(
    private val dao: PolicyDao,
    private val access: DocumentAccess,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val grants: Flow<List<Grant>> = dao.grants().map(::toGrants)

    suspend fun current(): List<Grant> = toGrants(dao.allGrants())

    /** Grants [uri], or returns the existing grant if it is already granted. */
    suspend fun add(uri: Uri, kind: GrantKind): Grant {
        val existing = current()
        existing.firstOrNull { it.uri == uri }?.let { return it }
        access.take(uri)
        val name = uniqueName(access.displayName(uri, kind), kind, existing.map { it.name }.toSet())
        val grantedAt = clock()
        val id = dao.addGrant(GrantEntity(uri = uri.toString(), name = name, kind = kind.name, grantedAt = grantedAt))
        return Grant(id, uri, name, kind, grantedAt, available = true)
    }

    suspend fun revoke(grant: Grant) {
        dao.removeGrant(grant.id)
        releaseQuietly(grant.uri)
    }

    /** Revokes every grant, as Clear all data does. */
    suspend fun clear() {
        dao.allGrants().forEach { releaseQuietly(Uri.parse(it.uri)) }
        dao.resetGrants()
    }

    private fun releaseQuietly(uri: Uri) {
        try {
            access.release(uri)
        } catch (e: SecurityException) {
            // Android had already dropped the access; the grant is gone either way.
        }
    }

    private fun toGrants(rows: List<GrantEntity>): List<Grant> {
        val held = access.persisted()
        return rows.map { row ->
            val uri = Uri.parse(row.uri)
            // An unreadable kind counts as a file: a file grant never resolves anything beneath it.
            val kind = GrantKind.entries.firstOrNull { it.name == row.kind } ?: GrantKind.FILE
            Grant(row.id, uri, row.name, kind, row.grantedAt, uri in held)
        }
    }

    private fun uniqueName(displayName: String?, kind: GrantKind, taken: Set<String>): String {
        val base = PathRules.cleanName(displayName) ?: if (kind == GrantKind.FOLDER) "Folder" else "File"
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base ($it)" }.first { it !in taken }
    }
}

sealed interface PathResolution {
    /**
     * [path] lies inside [grant]. [document] is what the path names, or null when its last segment
     * does not exist yet; [parent] is the folder that holds or would hold it (null for a file grant
     * or a folder grant's own root).
     */
    data class Found(val grant: Grant, val document: DocumentRef?, val parent: DocumentRef?, val name: String) : PathResolution

    /** [reason] is for the model and never repeats the path. */
    data class Refused(val reason: String) : PathResolution
}

/** The syntax of a path the model gives: `<grant name>/<segment>/...`. */
object PathRules {
    const val MAX_LENGTH = 1024
    const val MAX_SEGMENTS = 32

    /** Splits [path] into segments, or returns null if any segment could name something other than a plain child. */
    fun segments(path: String): List<String>? {
        if (path.isEmpty() || path.length > MAX_LENGTH || path.startsWith("/")) return null
        val segments = path.trimEnd('/').split('/')
        if (segments.size > MAX_SEGMENTS) return null
        return segments.takeIf { it.all(::isPlainName) }
    }

    /** A grant name built from a display name, or null if nothing usable is left. */
    fun cleanName(displayName: String?): String? =
        displayName?.replace('/', '_')?.filterNot { it.isISOControl() }?.trim()?.take(MAX_NAME)?.trim()?.takeIf(::isPlainName)

    /** Grant names go into the system prompt, so they are kept short. */
    private const val MAX_NAME = 80

    private fun isPlainName(segment: String) =
        segment.isNotBlank() && segment != "." && segment != ".." && segment.none { it == '\\' || it.isISOControl() }
}

/**
 * Resolves the model's paths against the user's grants by walking each folder's real children, so
 * a path can only reach documents the provider lists inside a granted tree: `..`, absolute paths
 * and look-alike names are refused rather than interpreted.
 */
class GrantScope(private val grants: GrantStore, private val access: DocumentAccess) {
    /** The names the model starts paths with, for grants Android still honours. */
    suspend fun names(): List<String> = grants.current().filter { it.available }.map { it.name }

    suspend fun resolve(path: String): PathResolution {
        val segments = PathRules.segments(path) ?: return PathResolution.Refused("The path is not valid. Use the granted name, then folder and file names separated by '/'.")
        val grant = grants.current().firstOrNull { it.name == segments.first() }
            ?: return PathResolution.Refused("That is not inside a file or folder the user has granted.")
        if (!grant.available) return PathResolution.Refused("The user's grant for that is no longer available.")
        val rest = segments.drop(1)
        if (grant.kind == GrantKind.FILE) {
            return if (rest.isEmpty()) {
                PathResolution.Found(grant, DocumentRef(grant.uri, isDirectory = false), null, grant.name)
            } else {
                PathResolution.Refused("A granted file has nothing inside it.")
            }
        }
        var folder = access.root(grant.uri)
        if (rest.isEmpty()) return PathResolution.Found(grant, folder, null, grant.name)
        rest.forEachIndexed { index, segment ->
            val matches = access.children(grant.uri, folder).filter { it.first == segment }.map { it.second }
            if (matches.size > 1) return PathResolution.Refused("More than one item has that name, so it is ambiguous.")
            val match = matches.singleOrNull()
            val last = index == rest.lastIndex
            if (last) return PathResolution.Found(grant, match, folder, segment)
            if (match == null || !match.isDirectory) return PathResolution.Refused("A folder on that path does not exist.")
            folder = match
        }
        error("unreachable: the loop returns on the last segment")
    }

    /** The policy engine's scope check: a skill that acts on files names its target in a `path` argument. */
    suspend fun check(request: SkillRequest): ScopeCheck {
        val path = request.arguments.string(PATH_ARGUMENT) ?: return ScopeCheck.OutOfScope("This skill needs a path inside a granted file or folder.")
        return when (val resolution = resolve(path)) {
            is PathResolution.Found -> ScopeCheck.InScope(listOf(target(path, resolution)))
            is PathResolution.Refused -> ScopeCheck.OutOfScope(resolution.reason)
        }
    }

    /** Bound by an approval: the document itself, or the folder and name it would be created as. */
    private fun target(path: String, found: PathResolution.Found): ResourceTarget {
        val identity = found.document?.uri?.toString() ?: "${found.parent?.uri}/${found.name}"
        return ResourceTarget(PathRules.segments(path)!!.joinToString("/"), identity)
    }

    companion object {
        const val PATH_ARGUMENT = "path"
    }
}
