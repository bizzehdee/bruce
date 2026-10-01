package com.bizzeh.bruce.inference

import java.io.File
import java.security.MessageDigest

/**
 * Evaluated fixed prompts kept on storage, at most one per model file (TASK-109), so the first reply
 * after a model loads does not read the system prompt and skill descriptions again. A file is named
 * after its model and everything its contents depend on: the model file, the context length, the
 * prompt text and the build of the app (and so of llama.cpp); any change makes a new one, and the
 * model's older file is deleted. Callers only save prompts that hold nothing about the user.
 */
class SavedPrompts(private val dir: File, private val build: String) {
    /** Where the state for [prefix] on [model] at [contextLength] is kept; the model's other files are deleted. */
    fun fileFor(model: File, contextLength: Int, prefix: String): File {
        val owner = owner(model)
        val key = hash(listOf(owner, model.length(), model.lastModified(), contextLength, build, prefix).joinToString("\u0000"))
        val file = File(dir, "$owner-$key$SUFFIX")
        dir.mkdirs()
        filesOf(owner).filter { it != file }.forEach(File::delete)
        return file
    }

    /** Deletes [model]'s saved state, when the model is deleted. */
    fun forget(model: File) = filesOf(owner(model)).forEach(File::delete)

    private fun filesOf(owner: String): List<File> =
        dir.listFiles { file -> file.name.startsWith("$owner-") && file.name.endsWith(SUFFIX) }.orEmpty().toList()

    private fun owner(model: File): String = hash(model.absolutePath).take(OWNER_CHARS)

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(KEY_CHARS)

    private companion object {
        const val SUFFIX = ".state"
        const val OWNER_CHARS = 16
        const val KEY_CHARS = 32
    }
}
