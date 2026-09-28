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
    fun followsRedirects() {
        assertEquals(200, transport.get("$base/moved", emptyMap(), maxBytes = 1_000)!!.status)
    }

    @Test
    fun unreachableServerThrowsIOException() {
        val port = server.address.port
        server.stop(0)

        assertThrows<IOException> { transport.get("http://127.0.0.1:$port/ok", emptyMap(), maxBytes = 1_000) }
    }
}
