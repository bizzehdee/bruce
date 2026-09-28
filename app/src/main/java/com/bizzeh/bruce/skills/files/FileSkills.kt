package com.bizzeh.bruce.skills.files

import com.bizzeh.bruce.policy.DocumentAccess
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.GrantScope
import com.bizzeh.bruce.policy.PathResolution
import com.bizzeh.bruce.policy.PathRules
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Skills that act inside the user's granted files and folders. The policy engine has already
 * checked the path against the grants; each skill resolves it again to act on the same document.
 */
class FileSkills(private val scope: GrantScope, private val access: DocumentAccess, private val io: CoroutineDispatcher) {
    fun create(): List<Skill> = listOf(listFiles(), readFile(), createFile(), writeFile())

    private fun pathParameter(description: String) =
        Parameter(GrantScope.PATH_ARGUMENT, ParameterType.STRING, description, maxLength = PathRules.MAX_LENGTH)

    private fun listFiles() = Skill(
        id = "list_files",
        version = 1,
        description = "List the files and folders inside a granted folder. Folders end with '/'.",
        input = InputSchema(listOf(pathParameter("The folder, starting with a granted name, for example Documents or Documents/notes"))),
        capabilities = setOf(Capability.FILE_READ),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.GRANTED_FILES,
    ) { arguments -> withContext(io) { list(arguments) } }

    private fun readFile() = Skill(
        id = "read_file",
        version = 1,
        description = "Read a plain-text file in a granted folder. Long files are cut short.",
        input = InputSchema(listOf(pathParameter("The file, starting with a granted name, for example Documents/notes/todo.txt"))),
        capabilities = setOf(Capability.FILE_READ),
        defaultState = SkillState.DECLINED,
        scope = ResourceScope.GRANTED_FILES,
    ) { arguments -> withContext(io) { read(arguments) } }

    private fun contentParameter() = Parameter(CONTENT_ARGUMENT, ParameterType.STRING, "The complete text the file will hold", maxLength = MAX_WRITE_CHARS)

    private fun createFile() = Skill(
        id = "create_file",
        version = 1,
        description = "Create a new plain-text file in a granted folder. Fails if a file with that name already exists.",
        input = InputSchema(listOf(pathParameter("The new file, starting with a granted name, for example Documents/notes/ideas.txt"), contentParameter())),
        capabilities = setOf(Capability.FILE_CREATE),
        defaultState = SkillState.ASK,
        scope = ResourceScope.GRANTED_FILES,
    ) { arguments -> withContext(io) { createNew(arguments) } }

    private fun writeFile() = Skill(
        id = "write_file",
        version = 1,
        description = "Replace the whole contents of an existing plain-text file in a granted folder.",
        input = InputSchema(listOf(pathParameter("The file, starting with a granted name, for example Documents/notes/todo.txt"), contentParameter())),
        capabilities = setOf(Capability.FILE_WRITE),
        defaultState = SkillState.ASK,
        scope = ResourceScope.GRANTED_FILES,
    ) { arguments -> withContext(io) { replace(arguments) } }

    private suspend fun createNew(arguments: SkillArguments): SkillOutcome {
        val found = when (val resolution = resolve(arguments)) {
            is PathResolution.Found -> resolution
            is PathResolution.Refused -> return SkillOutcome.Failed(DenialCode.RESOURCE_OUTSIDE_SCOPE, resolution.reason)
        }
        if (found.document != null) return SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "Something with that name already exists. Use write_file to replace a file's contents.")
        val parent = found.parent ?: return SkillOutcome.Failed(DenialCode.RESOURCE_OUTSIDE_SCOPE, "A new file must go inside a granted folder.")
        val file = access.create(found.grant.uri, parent, found.name) ?: return SkillOutcome.Failed(DenialCode.TOOL_FAILED, "The folder did not accept a new file.")
        val content = arguments.string(CONTENT_ARGUMENT).orEmpty()
        access.write(file.uri, content.toByteArray())
        return SkillOutcome.Done("Created the file (${content.length} characters).")
    }

    private suspend fun replace(arguments: SkillArguments): SkillOutcome {
        val found = when (val resolution = resolve(arguments)) {
            is PathResolution.Found -> resolution
            is PathResolution.Refused -> return SkillOutcome.Failed(DenialCode.RESOURCE_OUTSIDE_SCOPE, resolution.reason)
        }
        val file = found.document ?: return SkillOutcome.Failed(DenialCode.RESOURCE_NOT_FOUND, "No file exists at that path. Use create_file to make one.")
        if (file.isDirectory) return SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "That is a folder, not a file.")
        // Only text is replaced with text, so a photo or document cannot be overwritten by mistake.
        if (!PlainText.allowedType(access.mimeType(file.uri))) return notPlainText()
        val existing = access.read(file.uri, MAX_READ_BYTES) ?: return notFound()
        if (PlainText.decode(existing) == null) return notPlainText()
        val content = arguments.string(CONTENT_ARGUMENT).orEmpty()
        access.write(file.uri, content.toByteArray())
        return SkillOutcome.Done("Replaced the file's contents (${content.length} characters).")
    }

    private suspend fun list(arguments: SkillArguments): SkillOutcome {
        val found = when (val resolution = resolve(arguments)) {
            is PathResolution.Found -> resolution
            is PathResolution.Refused -> return SkillOutcome.Failed(DenialCode.RESOURCE_OUTSIDE_SCOPE, resolution.reason)
        }
        val folder = found.document ?: return notFound()
        if (!folder.isDirectory) return SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "That is a file. Use read_file to read it.")
        val names = access.children(found.grant.uri, folder)
            .map { (name, ref) -> if (ref.isDirectory) "$name/" else name }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (names.isEmpty()) return SkillOutcome.Done("The folder is empty.")
        val shown = names.take(MAX_LISTED).joinToString("\n")
        return SkillOutcome.Done(if (names.size > MAX_LISTED) "$shown\n[${names.size - MAX_LISTED} more not shown]" else shown)
    }

    private suspend fun read(arguments: SkillArguments): SkillOutcome {
        val found = when (val resolution = resolve(arguments)) {
            is PathResolution.Found -> resolution
            is PathResolution.Refused -> return SkillOutcome.Failed(DenialCode.RESOURCE_OUTSIDE_SCOPE, resolution.reason)
        }
        val file: DocumentRef = found.document ?: return notFound()
        if (file.isDirectory) return SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "That is a folder. Use list_files to see what is in it.")
        if (!PlainText.allowedType(access.mimeType(file.uri))) return notPlainText()
        val bytes = access.read(file.uri, MAX_READ_BYTES + 1) ?: return notFound()
        val cut = bytes.size > MAX_READ_BYTES
        val text = PlainText.decode(if (cut) bytes.copyOf(MAX_READ_BYTES) else bytes) ?: return notPlainText()
        return SkillOutcome.Done(if (cut) "$text\n[Only the start of this file was read.]" else text)
    }

    private suspend fun resolve(arguments: SkillArguments): PathResolution =
        scope.resolve(arguments.string(GrantScope.PATH_ARGUMENT).orEmpty())

    private fun notFound() = SkillOutcome.Failed(DenialCode.RESOURCE_NOT_FOUND, "Nothing exists at that path.")

    private fun notPlainText() = SkillOutcome.Failed(DenialCode.TOOL_UNAVAILABLE, "That file is not plain text, so it cannot be read.")

    companion object {
        /** More than the model is given (ToolOutput caps results), so a cut is reported honestly. */
        const val MAX_READ_BYTES = 16 * 1024
        const val MAX_LISTED = 200
        /** Inside InputSchema.MAX_RAW_LENGTH, which bounds the whole arguments object. */
        const val MAX_WRITE_CHARS = 15_000
        private const val CONTENT_ARGUMENT = "content"
    }
}

/** Plain text only: a text MIME type (or none given) and valid UTF-8 without NUL bytes. */
object PlainText {
    private const val BYTE_ORDER_MARK = "\uFEFF"
    private val TEXT_TYPES = setOf(
        "application/json", "application/xml", "application/x-yaml", "application/yaml", "application/toml",
        "application/javascript", "application/x-sh", "application/csv", "application/x-subrip", "application/octet-stream",
    )

    fun allowedType(mimeType: String?): Boolean =
        mimeType == null || mimeType.startsWith("text/") || mimeType.substringBefore(';').trim() in TEXT_TYPES

    /** The text, or null if the bytes are not UTF-8 text. A multi-byte character cut at the end is dropped. */
    fun decode(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val out = CharBuffer.allocate(bytes.size)
        val result = decoder.decode(ByteBuffer.wrap(bytes), out, false)
        if (result.isError) return null
        return out.flip().toString().removePrefix(BYTE_ORDER_MARK)
    }
}
