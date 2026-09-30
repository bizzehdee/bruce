package com.bizzeh.bruce.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ChatImagesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "0d49eb73-53a0-464b-9e5a-3eca3d64eb9d.jpg"

    @Test
    fun onlyBrucesOwnNamesAreUsed() {
        val dir = temp.newFolder()
        val images = ChatImages(dir, context.contentResolver)
        File(dir, name).writeText("x")
        File(dir, "notes.txt").writeText("x")

        assertEquals(File(dir, name), images.file(name))
        assertNull(images.file("notes.txt"))
        assertNull(images.file("../$name"))
        assertNull(images.file("0d49eb73-53a0-464b-9e5a-3eca3d64eb9e.jpg"))
        assertFalse(ChatImages.isName("/data/$name"))
    }

    @Test
    fun unusedImagesAreDeleted() {
        val dir = temp.newFolder()
        val kept = File(dir, name).apply { writeText("x") }
        val other = File(dir, "11111111-2222-3333-4444-555555555555.jpg").apply { writeText("x") }

        ChatImages(dir, context.contentResolver).keepOnly(setOf(name))

        assertTrue(kept.exists())
        assertFalse(other.exists())
    }

    @Test
    fun storedImagesAreReadAsRgbAndThumbnails() {
        val dir = temp.newFolder()
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF102030.toInt()) }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val images = ChatImages(dir, context.contentResolver)

        val rgb = images.rgb(name)!!
        assertEquals(4, rgb.width)
        assertEquals(2, rgb.height)
        assertEquals(24, rgb.rgb.size)
        assertEquals(4, images.thumbnail(name, 480)!!.width)
        assertArrayEquals(File(dir, name).readBytes(), images.jpeg(name))
        assertNull(images.rgb("11111111-2222-3333-4444-555555555555.jpg"))
    }

    @Test
    fun largeImagesAreScaledToFitAndPixelsLoseTheirAlpha() {
        assertEquals(1024 to 512, ChatImages.fitted(4000, 2000))
        assertEquals(384 to 1024, ChatImages.fitted(750, 2000))
        assertEquals(640 to 488, ChatImages.fitted(640, 488))
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x30, 0x7f, 0x00, 0xff.toByte()), ChatImages.rgbBytes(intArrayOf(0xFF102030.toInt(), 0x807F00FF.toInt())))
    }
}
