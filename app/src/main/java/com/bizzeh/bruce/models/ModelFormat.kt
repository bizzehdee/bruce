package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.Gguf
import java.io.File

/** Which pipeline a model file belongs to (TASK-120). */
enum class ModelFormat {
    GGUF,
    LITERT_LM,
}

object ModelFiles {
    const val LITERT_LM_EXTENSION = ".litertlm"
    const val GGUF_EXTENSION = ".gguf"

    /**
     * [file]'s format: by extension for LiteRT-LM (no documented magic bytes were found for
     * `.litertlm`, TASK-120 research); by magic bytes for GGUF when [file] exists, falling back to
     * its extension when it does not (e.g. a destination not yet downloaded to); null for neither.
     */
    fun formatOf(file: File): ModelFormat? = when {
        file.name.endsWith(LITERT_LM_EXTENSION, ignoreCase = true) -> ModelFormat.LITERT_LM
        file.isFile && Gguf.hasMagic(file) -> ModelFormat.GGUF
        !file.exists() && file.name.endsWith(GGUF_EXTENSION, ignoreCase = true) -> ModelFormat.GGUF
        else -> null
    }

    /** Installed model files in [dir]: anything a known pipeline can load. */
    fun installedModels(dir: File): List<File> =
        dir.listFiles { file -> file.isFile && (file.name.endsWith(GGUF_EXTENSION, ignoreCase = true) || file.name.endsWith(LITERT_LM_EXTENSION, ignoreCase = true)) }
            ?.toList().orEmpty()
}
