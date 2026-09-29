package com.bizzeh.bruce.chat

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bizzeh.bruce.huggingface.HttpTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ImageResult {
    data class Loaded(val image: ImageBitmap) : ImageResult

    /** The network mode does not allow this address. */
    data object NotAllowed : ImageResult

    data object Failed : ImageResult
}

/**
 * Fetches an image from a reply, only when the user asks for it: an address the model wrote can
 * carry chat text to any server, so nothing is fetched on its own. No cookies or credentials are
 * sent (the transport sets none), and the image is capped in bytes and in pixels.
 */
class RemoteImages(
    private val transport: HttpTransport,
    /** Whether the network mode allows fetching from [host]; asked again for every image. */
    private val allowed: suspend (host: String) -> Boolean,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
) {
    private val loaded = object : LinkedHashMap<String, ImageBitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX_CACHED
    }

    suspend fun load(url: String): ImageResult {
        synchronized(loaded) { loaded[url] }?.let { return ImageResult.Loaded(it) }
        val host = host(url) ?: return ImageResult.Failed
        if (!allowed(host)) return ImageResult.NotAllowed
        return withContext(io) {
            val response = try {
                transport.get(url, mapOf("User-Agent" to userAgent, "Accept" to "image/png,image/jpeg,image/webp,image/gif"), MAX_BYTES)
            } catch (e: IOException) {
                null
            } ?: return@withContext ImageResult.Failed
            val type = response.headers["content-type"].orEmpty()
            if (response.status != 200 || !type.startsWith("image/")) return@withContext ImageResult.Failed
            val image = decode(response.body) ?: return@withContext ImageResult.Failed
            synchronized(loaded) { loaded[url] = image }
            ImageResult.Loaded(image)
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val MAX_SIDE = 2048
        private const val MAX_CACHED = 8

        /** The host of an https address, or null for anything else. */
        fun host(url: String): String? = try {
            java.net.URI(url.trim()).takeIf { it.scheme.equals("https", ignoreCase = true) }?.host?.lowercase()
        } catch (e: java.net.URISyntaxException) {
            null
        }
    }
}
