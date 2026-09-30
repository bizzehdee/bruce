package com.bizzeh.bruce.models

import com.bizzeh.bruce.huggingface.HubFile
import java.io.File

/**
 * Vision projectors (TASK-083): the `mmproj` GGUF a vision model needs to see images. A model's
 * projector is kept in [DIR] beside it under the model's own file name, so it is never listed as a
 * model and goes when the model goes.
 */
object Projectors {
    const val DIR = "projectors"

    fun fileFor(model: File): File = File(File(model.parentFile, DIR), model.name)

    fun installedFor(model: File): File? = fileFor(model).takeIf { it.isFile }

    fun bytesFor(model: File): Long = installedFor(model)?.length() ?: 0

    /** llama.cpp's conversion names projectors `mmproj-…`, and repositories keep the name. */
    fun isProjector(path: String): Boolean = path.substringAfterLast('/').contains("mmproj", ignoreCase = true)

    /** A repository's projector: F16, llama.cpp's usual type, where there is one; otherwise the smallest. */
    fun choose(files: List<HubFile>): HubFile? {
        val projectors = files.filter { isProjector(it.path) }
        return projectors.firstOrNull { F16.containsMatchIn(it.path.substringAfterLast('/')) } ?: projectors.minByOrNull { it.sizeBytes }
    }

    private val F16 = Regex("(?i)(^|[-_.])f16([-_.]|$)")
}
