package com.bizzeh.bruce.models

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.bizzeh.bruce.gguf.Gguf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

sealed interface ImportResult {
    data class Imported(val file: File) : ImportResult

    data class Failed(val error: ImportError) : ImportResult
}

enum class ImportError {
    UNREADABLE,
    NOT_GGUF,
    INSUFFICIENT_STORAGE,
    COPY_FAILED,
}

/** Copies a user-picked GGUF file (Storage Access Framework URI) into app-private model storage. */
class ModelImporter(
    private val contentResolver: ContentResolver,
    private val modelsDir: File,
    private val dispatcher: CoroutineDispatcher,
    private val usableSpace: (File) -> Long = File::getUsableSpace,
) {
    private class SourceDetails(val displayName: String?, val sizeBytes: Long?)

    suspend fun import(uri: Uri): ImportResult = withContext(dispatcher) {
        val details = queryDetails(uri)
        modelsDir.mkdirs()
        if (details.sizeBytes != null && details.sizeBytes > usableSpace(modelsDir)) {
            return@withContext ImportResult.Failed(ImportError.INSUFFICIENT_STORAGE)
        }
        val stream = openStream(uri) ?: return@withContext ImportResult.Failed(ImportError.UNREADABLE)
        stream.use { copyIfGguf(it, sanitisedFileName(details.displayName)) }
    }

    private fun copyIfGguf(source: InputStream, fileName: String): ImportResult {
        val header = Gguf.readHeader(source)
        if (!Gguf.hasMagic(header)) return ImportResult.Failed(ImportError.NOT_GGUF)

        val target = uniqueTarget(modelsDir, fileName)
        val partial = File(modelsDir, ".${target.name}.partial")
        val copied = try {
            partial.outputStream().use { out ->
                out.write(header)
                source.copyTo(out)
            }
            true
        } catch (e: IOException) {
            false
        }
        if (!copied || !partial.renameTo(target)) {
            partial.delete()
            return ImportResult.Failed(ImportError.COPY_FAILED)
        }
        return ImportResult.Imported(target)
    }

    private fun queryDetails(uri: Uri): SourceDetails {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                return SourceDetails(
                    displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null,
                    sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null,
                )
            }
        }
        return SourceDetails(displayName = null, sizeBytes = null)
    }

    private fun openStream(uri: Uri): InputStream? = try {
        contentResolver.openInputStream(uri)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: SecurityException) {
        null
    }

    internal companion object {
        private const val GGUF_EXTENSION = ".gguf"
        private const val MAX_BASE_NAME_LENGTH = 120
        private val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9._-]")
        private val GGUF_SUFFIX = Regex("\\.gguf$", RegexOption.IGNORE_CASE)

        /** [fileName] in [dir], or with "-1", "-2"… before the extension if that name is taken. */
        fun uniqueTarget(dir: File, fileName: String): File {
            val base = fileName.removeSuffix(GGUF_EXTENSION)
            var candidate = File(dir, fileName)
            var suffix = 1
            while (candidate.exists()) {
                candidate = File(dir, "$base-$suffix$GGUF_EXTENSION")
                suffix++
            }
            return candidate
        }

        /** The display name comes from another app, so it is never trusted as a path. */
        fun sanitisedFileName(displayName: String?): String {
            val base = (displayName ?: "")
                .substringAfterLast('/')
                .replace(UNSAFE_CHARACTERS, "_")
                .trimStart('.')
                .replace(GGUF_SUFFIX, "")
                .take(MAX_BASE_NAME_LENGTH)
                .ifEmpty { "model" }
            return base + GGUF_EXTENSION
        }
    }
}
