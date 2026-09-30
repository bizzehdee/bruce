package com.bizzeh.bruce.chat

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {
    @Test
    fun sharedTextBecomesADocumentNamedByItsSubject() {
        val shared = ShareIntents.read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.com/story").putExtra(Intent.EXTRA_SUBJECT, "A story\u0007"))!!

        assertEquals(ChatDocument("A story", "https://example.com/story"), shared.text)
        assertTrue(shared.images.isEmpty() && shared.files.isEmpty())
        assertEquals("Shared text", ShareIntents.read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hi"))!!.text!!.name)
    }

    @Test
    fun imagesAndFilesAreReturnedForStoring() {
        val a = Uri.parse("content://photos/a")
        val b = Uri.parse("content://photos/b")
        val pdf = Uri.parse("content://files/report.pdf")

        assertEquals(listOf(a), ShareIntents.read(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, a))!!.images)
        val many = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b, a, b, a))
        assertEquals(MAX_ATTACHMENTS, ShareIntents.read(many)!!.images.size)
        assertEquals(listOf(pdf), ShareIntents.read(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, pdf))!!.files)
    }

    @Test
    fun otherIntentsAndEmptySharesAreIgnored() {
        assertNull(ShareIntents.read(null))
        assertNull(ShareIntents.read(Intent(Intent.ACTION_VIEW)))
        assertNull(ShareIntents.read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "  ")))
    }
}
