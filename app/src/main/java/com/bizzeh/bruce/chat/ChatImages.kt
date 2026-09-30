package com.bizzeh.bruce.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.bizzeh.bruce.inference.RgbImage
import com.bizzeh.bruce.runtime.ImageSource
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Images attached to chats (TASK-082), kept in app storage as JPEG copies no larger than
 * [MAX_SIDE] pixels a side, named by a random id. The picked original is read once and never
 * kept. Names from saved chats are checked before use, so a name can never reach outside [dir].
 */
class ChatImages(private val dir: File, private val resolver: ContentResolver) : ImageSource {
    /** Copies a picked image; its name, or null when it cannot be read as an image. */
    fun save(uri: Uri): String? {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (width, height) = fitted(info.size.width, info.size.height)
                decoder.setTargetSize(width, height)
            }
        } catch (e: IOException) {
            Log.w(TAG, "image unreadable: ${e.javaClass.simpleName}")
            return null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "image unreadable: ${e.javaClass.simpleName}")
            return null
        } catch (e: SecurityException) {
            Log.w(TAG, "image not readable by Bruce")
            return null
        }
        dir.mkdirs()
        val name = UUID.randomUUID().toString() + EXTENSION
        return try {
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        } catch (e: IOException) {
            Log.w(TAG, "image not saved: ${e.javaClass.simpleName}")
            File(dir, name).delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** The stored copy, or null for a name that is not one of Bruce's or a file that has gone. */
    fun file(name: String): File? = name.takeIf(::isName)?.let { File(dir, it) }?.takeIf { it.isFile }

    /** The image as a model takes it. */
    override fun rgb(name: String): RgbImage? {
        val bitmap = file(name)?.let { BitmapFactory.decodeFile(it.path) } ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            RgbImage(bitmap.width, bitmap.height, rgbBytes(pixels))
        } finally {
            bitmap.recycle()
        }
    }

    /** A copy no larger than about [maxSide] pixels a side, for showing it. */
    fun thumbnail(name: String, maxSide: Int): Bitmap? {
        val file = file(name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The stored JPEG's bytes, as a server takes it. */
    override fun jpeg(name: String): ByteArray? = file(name)?.readBytes()

    /** Deletes every stored image not in [used], such as those of deleted chats. */
    fun keepOnly(used: Set<String>) {
        dir.listFiles().orEmpty().filter { it.name !in used }.forEach { it.delete() }
    }

    companion object {
        const val DIR = "chat-images"
        const val MAX_SIDE = 1024
        private const val JPEG_QUALITY = 90
        private const val EXTENSION = ".jpg"
        private const val TAG = "BruceImages"
        private val NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")

        fun isName(name: String): Boolean = NAME.matches(name)

        /** [width] by [height] scaled down to fit [MAX_SIDE], keeping its shape; never scaled up. */
        fun fitted(width: Int, height: Int): Pair<Int, Int> {
            val longest = maxOf(width, height)
            if (longest <= MAX_SIDE) return width to height
            val scale = MAX_SIDE.toDouble() / longest
            return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
        }

        /** ARGB pixels as 3-byte RGB; alpha is dropped. */
        fun rgbBytes(pixels: IntArray): ByteArray {
            val rgb = ByteArray(pixels.size * 3)
            pixels.forEachIndexed { index, pixel ->
                rgb[index * 3] = (pixel shr 16).toByte()
                rgb[index * 3 + 1] = (pixel shr 8).toByte()
                rgb[index * 3 + 2] = pixel.toByte()
            }
            return rgb
        }
    }
}
