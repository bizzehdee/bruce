package com.bizzeh.bruce.huggingface

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetSocketAddress

/** Exercises the real HttpURLConnection path against the JDK's local HTTP server. */
class UrlConnectionTransportTest {
    private lateinit var server: HttpServer
    private val transport = UrlConnectionTransport(connectTimeoutMs = 2_000, readTimeoutMs = 2_000)
    private lateinit var base: String
    private var lastUserAgent: String? = null

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            lastUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            exchange.responseHeaders.add("X-Linked-Size", "42")
            val body = "hello".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { exchange ->
            val body = "{\"error\":\"nope\"}".toByteArray()
            exchange.sendResponseHeaders(404, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/big") { exchange ->
            val body = ByteArray(100_000)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/moved") { exchange ->
            exchange.responseHeaders.add("Location", "/ok")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun returnsStatusBodyAndLowercasedHeaders() {
        val response = transport.get("$base/ok", mapOf("User-Agent" to "Bruce/test"), maxBytes = 1_000)!!

        assertEquals(200, response.status)
        assertArrayEquals("hello".toByteArray(), response.body)
        assertEquals("42", response.headers["x-linked-size"])
        assertEquals("Bruce/test", lastUserAgent)
    }

    @Test
    fun readsErrorBodies() {
        val response = transport.get("$base/missing", emptyMap(), maxBytes = 1_000)!!

        assertEquals(404, response.status)
        assertEquals("{\"error\":\"nope\"}", response.body.toString(Charsets.UTF_8))
    }

    @Test
    fun oversizedBodyIsRefused() {
        assertNull(transport.get("$base/big", emptyMap(), maxBytes = 10_000))
    }

    @Test
    fun openStreamsTheBody() {
        transport.open("$base/big", emptyMap()).use { response ->
            assertEquals(200, response.status)
            assertEquals(100_000, response.body.readBytes().size)
        }
    }

    @Test
    fun followsRedirects() {
        assertEquals(200, transport.get("$base/moved", emptyMap(), maxBytes = 1_000)!!.status)
    }

    @Test
    fun tokenIsKeptOnSameHostRedirectsButDroppedWhenTheHostChanges() {
        val cdn = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var cdnAuth: String? = "unset"
        cdn.createContext("/file") { exchange ->
            cdnAuth = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        cdn.start()
        var sameHostAuth: String? = null
        server.createContext("/to-cdn") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${cdn.address.port}/file")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/same") { exchange ->
            exchange.responseHeaders.add("Location", "/landing")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.createContext("/landing") { exchange ->
            sameHostAuth = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        try {
            transport.open("$base/to-cdn", mapOf("Authorization" to "Bearer tok")).close()
            transport.open("$base/same", mapOf("Authorization" to "Bearer tok")).close()
        } finally {
            cdn.stop(0)
        }

        assertNull(cdnAuth)
        assertEquals("Bearer tok", sameHostAuth)
    }

    @Test
    fun redirectRules() {
        val auth = mapOf("Authorization" to "Bearer tok", "User-Agent" to "Bruce")
        val hub = java.net.URL("https://huggingface.co/a/b/resolve/main/m.gguf")

        val (cdn, cdnHeaders) = redirect(hub, "https://cdn.example.org/xet/123?sig=1", auth)
        assertEquals("cdn.example.org", cdn.host)
        assertEquals(mapOf("User-Agent" to "Bruce"), cdnHeaders)

        val (renamed, renamedHeaders) = redirect(hub, "/a/b-moved/resolve/main/m.gguf", auth)
        assertEquals("https://huggingface.co/a/b-moved/resolve/main/m.gguf", renamed.toString())
        assertEquals(auth, renamedHeaders)

        assertEquals(mapOf("User-Agent" to "Bruce"), redirect(hub, "https://huggingface.co:8443/x", auth).second)
        assertThrows<IOException> { redirect(hub, "http://huggingface.co/a/b", auth) }
        assertEquals(auth, redirect(java.net.URL("http://127.0.0.1:1/a"), "/b", auth).second)
    }

    @Test
    fun redirectLoopsAreCapped() {
        server.createContext("/loop") { exchange ->
            exchange.responseHeaders.add("Location", "/loop")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        assertThrows<IOException> { transport.open("$base/loop", emptyMap()) }
    }

    @Test
    fun postFormEncodesTheBody() {
        var received = ""
        var contentType: String? = null
        server.createContext("/token") { exchange ->
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            received = exchange.requestBody.readBytes().decodeToString()
            val body = "{\"ok\":true}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        val response = transport.postForm("$base/token", emptyMap(), mapOf("code" to "a b&c", "x" to "1"), maxBytes = 1_000)!!

        assertEquals(200, response.status)
        assertEquals("code=a+b%26c&x=1", received)
        assertEquals("application/x-www-form-urlencoded", contentType)
    }

    @Test
    fun postFormErrorBodyAndSizeLimit() {
        server.createContext("/bad") { exchange ->
            val body = ByteArray(5_000)
            exchange.sendResponseHeaders(400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        assertEquals(400, transport.postForm("$base/bad", emptyMap(), emptyMap(), maxBytes = 10_000)!!.status)
        assertNull(transport.postForm("$base/bad", emptyMap(), emptyMap(), maxBytes = 100))
    }

    @Test
    fun unreachableServerThrowsIOException() {
        val port = server.address.port
        server.stop(0)

        assertThrows<IOException> { transport.get("http://127.0.0.1:$port/ok", emptyMap(), maxBytes = 1_000) }
    }
}
