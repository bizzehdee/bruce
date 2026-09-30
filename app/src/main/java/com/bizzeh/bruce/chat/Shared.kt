package com.bizzeh.bruce.chat

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.bizzeh.bruce.skills.ToolOutput

/** What another app shared with Bruce (TASK-100). */
data class Shared(val text: ChatDocument?, val images: List<Uri>, val files: List<Uri>)

object ShareIntents {
    private const val MAX_TEXT = 48_000
    private const val MAX_IMAGES = MAX_ATTACHMENTS
    private const val SHARED_NAME = "Shared text"

    /**
     * Reads a share: its text becomes a document (untrusted data, never instructions), images and
     * other files are returned for storing. Null for any other intent.
     */
    fun read(intent: Intent?): Shared? {
        if (intent == null || (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE)) return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { shared ->
            val name = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { ToolOutput.plain(it, 80).trim() }?.ifEmpty { null } ?: SHARED_NAME
            DocumentText.fit(name, shared, MAX_TEXT)
        }
        val streams = streams(intent)
        val type = intent.type.orEmpty()
        val images = if (type.startsWith("image/")) streams.take(MAX_IMAGES) else emptyList()
        val files = if (type.startsWith("image/")) emptyList() else streams.take(1)
        return Shared(text, images, files).takeIf { text != null || images.isNotEmpty() || files.isNotEmpty() }
    }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    } else {
        listOfNotNull(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }
}
