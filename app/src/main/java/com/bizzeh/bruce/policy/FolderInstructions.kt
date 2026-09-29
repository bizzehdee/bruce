package com.bizzeh.bruce.policy

import com.bizzeh.bruce.skills.FolderGuidance
import com.bizzeh.bruce.skills.files.PlainText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/**
 * Instructions found at a granted folder's root (TASK-049): its `AGENTS.md` and the files under
 * `.agents/`, nothing nested elsewhere and no other tools' files. [problem] says why they cannot be
 * used (too large, not text); [hash] covers every byte read, so any change asks the user again.
 */
data class FolderInstructions(
    val grantId: Long,
    val folder: String,
    val agentsMd: String?,
    /** Paths under `.agents/`, starting with the grant's name, as file skills take them. */
    val agentsFiles: List<String>,
    val hash: String,
    val problem: String? = null,
)

enum class InstructionsChoice { NONE_FOUND, UNDECIDED, FOLLOW, IGNORE }

/** Finds folders' instructions and keeps the user's choice about them. Nothing here can change grants, skills or confirmations. */
class FolderInstructionsStore(private val dao: PolicyDao, private val access: DocumentAccess) {
    /** Choices by grant id, with the hash they were made for. */
    val choices: Flow<Map<Long, FolderInstructionsEntity>> = dao.instructionChoices().map { rows -> rows.associateBy { it.grantId } }

    /** The folder's instructions as they are now, or null if it has none (or is not a folder Bruce can read). */
    fun read(grant: Grant): FolderInstructions? {
        if (grant.kind != GrantKind.FOLDER || !grant.available) return null
        val root = access.root(grant.uri)
        val top = access.children(grant.uri, root)
        val agentsMd = top.firstOrNull { (name, ref) -> name == AGENTS_MD && !ref.isDirectory }?.second
        val agentsDir = top.firstOrNull { (name, ref) -> name == AGENTS_DIR && ref.isDirectory }?.second
        if (agentsMd == null && agentsDir == null) return null
        val digest = MessageDigest.getInstance("SHA-256")
        var problem: String? = null
        val text = agentsMd?.let { ref ->
            val bytes = access.read(ref.uri, MAX_BYTES + 1) ?: ByteArray(0)
            digest.update(AGENTS_MD.toByteArray())
            digest.update(bytes)
            when {
                bytes.size > MAX_BYTES -> null.also { problem = "AGENTS.md is larger than ${MAX_BYTES / 1024} KB, so it is not used." }
                else -> PlainText.decode(bytes) ?: null.also { problem = "AGENTS.md is not plain text, so it is not used." }
            }
        }
        val files = agentsDir?.let { listAgents(grant, it, "${grant.name}/$AGENTS_DIR", digest) }.orEmpty()
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return FolderInstructions(grant.id, grant.name, text, files, hash, problem)
    }

    /** What the user decided for [instructions], or UNDECIDED if they have not, or the files changed since. */
    suspend fun choice(instructions: FolderInstructions?): InstructionsChoice {
        if (instructions == null) return InstructionsChoice.NONE_FOUND
        val stored = dao.instructionChoice(instructions.grantId)
        return when {
            stored == null || stored.hash != instructions.hash -> InstructionsChoice.UNDECIDED
            stored.follow -> InstructionsChoice.FOLLOW
            else -> InstructionsChoice.IGNORE
        }
    }

    suspend fun choose(instructions: FolderInstructions, follow: Boolean) {
        dao.setInstructionChoice(FolderInstructionsEntity(instructions.grantId, instructions.hash, follow))
    }

    /** The guidance a file skill carries for [grant]: only instructions the user chose to follow, unchanged since. */
    suspend fun guidance(grant: Grant): FolderGuidance? {
        val instructions = read(grant) ?: return null
        if (choice(instructions) != InstructionsChoice.FOLLOW || instructions.problem != null) return null
        val parts = listOfNotNull(
            instructions.agentsMd?.let { "From AGENTS.md:\n$it" },
            instructions.agentsFiles.takeIf { it.isNotEmpty() }?.let { "More instructions in this folder, to read with read_file when they apply:\n" + it.joinToString("\n") },
        )
        return FolderGuidance(instructions.hash, instructions.folder, parts.joinToString("\n\n"))
    }

    private fun listAgents(grant: Grant, folder: DocumentRef, path: String, digest: MessageDigest, depth: Int = 0): List<String> {
        if (depth > MAX_DEPTH) return emptyList()
        val found = mutableListOf<String>()
        for ((name, ref) in access.children(grant.uri, folder).sortedBy { it.first }) {
            if (found.size >= MAX_FILES) break
            val child = "$path/$name"
            if (ref.isDirectory) {
                found += listAgents(grant, ref, child, digest, depth + 1).take(MAX_FILES - found.size)
            } else {
                digest.update(child.toByteArray())
                digest.update(access.read(ref.uri, MAX_BYTES + 1) ?: ByteArray(0))
                found += child
            }
        }
        return found
    }

    companion object {
        const val AGENTS_MD = "AGENTS.md"
        const val AGENTS_DIR = ".agents"
        const val MAX_BYTES = 8 * 1024
        private const val MAX_FILES = 50
        private const val MAX_DEPTH = 3
    }
}
