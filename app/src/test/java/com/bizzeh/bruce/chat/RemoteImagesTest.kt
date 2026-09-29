package com.bizzeh.bruce.chat

import android.graphics.Bitmap
import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class RemoteImagesTest {
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private var response: HttpResponse? = null
    private var fail = false
    private var allow = true

    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse? {
            requests += url to headers
            if (fail) throw IOException("offline")
            assertEquals(RemoteImages.MAX_BYTES, maxBytes)
            return response
        }

        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("not used")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse? = error("not used")
    }
    private val hosts = mutableListOf<String>()
    private val images = RemoteImages(transport, { host -> hosts += host; allow }, Dispatchers.Unconfined, "Bruce/test")

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun load(url: String) = runBlocking { images.load(url) }

    @Test
    fun anAllowedImageIsFetchedWithoutCredentialsAndKept() {
        response = HttpResponse(200, mapOf("content-type" to "image/png"), png(40, 20))

        val loaded = load("https://Example.com/cat.png") as ImageResult.Loaded

        assertEquals(40, loaded.image.width)
        assertEquals(listOf("example.com"), hosts)
        val headers = requests.single().second
        assertEquals(setOf("User-Agent", "Accept"), headers.keys)
        assertTrue("fetched once, then kept", load("https://Example.com/cat.png") is ImageResult.Loaded)
        assertEquals(1, requests.size)
    }

    @Test
    fun largeImagesAreScaledDown() {
        response = HttpResponse(200, mapOf("content-type" to "image/png"), png(5000, 100))
        assertEquals(1250, (load("https://example.com/wide.png") as ImageResult.Loaded).image.width)
    }

    @Test
    fun theNetworkModeDecidesBeforeAnythingIsFetched() {
        allow = false
        assertEquals(ImageResult.NotAllowed, load("https://example.com/cat.png"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun otherSchemesFailuresAndNonImagesAreNotShown() {
        assertEquals(ImageResult.Failed, load("http://example.com/cat.png"))
        assertEquals(ImageResult.Failed, load("file:///sdcard/cat.png"))
        assertEquals(ImageResult.Failed, load("https://exa mple.com"))
        assertTrue(hosts.isEmpty())

        response = HttpResponse(200, mapOf("content-type" to "text/html"), "<html>".toByteArray())
        assertEquals(ImageResult.Failed, load("https://example.com/page"))
        response = HttpResponse(404, mapOf("content-type" to "image/png"), png(1, 1))
        assertEquals(ImageResult.Failed, load("https://example.com/missing.png"))
        response = HttpResponse(200, mapOf("content-type" to "image/png"), "not a png".toByteArray())
        assertEquals(ImageResult.Failed, load("https://example.com/broken.png"))
        response = null
        assertEquals("larger than the cap", ImageResult.Failed, load("https://example.com/huge.png"))
        fail = true
        assertEquals(ImageResult.Failed, load("https://example.com/offline.png"))
        assertNull(RemoteImages.host("ftp://example.com"))
    }
}
