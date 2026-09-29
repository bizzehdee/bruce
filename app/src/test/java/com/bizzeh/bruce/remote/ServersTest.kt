package com.bizzeh.bruce.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.huggingface.TokenCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException

/** Robolectric for Android's org.json. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServersTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("s.preferences_pb").also { it.delete() } } }
    private val cipher = object : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(sealed: ByteArray) = sealed.reversedArray()
    }
    private val store by lazy { RemoteServerStore(dataStore, cipher) }

    private var status = 200
    private var body = """{"object":"list","data":[{"id":"qwen3-8b"},{"id":""},{"id":"gemma-4"}]}"""
    private var offline = false
    private var networkAllowed = true
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()
    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("the key must not follow redirects")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("the key must not follow redirects")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun openSingle(url: String, headers: Map<String, String>): StreamingResponse {
            requests += url to headers
            if (offline) throw IOException("down")
            return StreamingResponse(status, emptyMap(), ByteArrayInputStream(body.toByteArray())) {}
        }
    }
    private val client = ServerClient(transport, Dispatchers.Unconfined, "Bruce/test") { networkAllowed }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun local() = ServerAddress.check("192.168.1.20:8080") as AddressCheck.Allowed

    @Test
    fun serversAreKeptWithTheirKeysSealedApart() = runBlocking {
        val server = store.add("  Home  ", local(), " secret-key ")
        val unnamed = store.add("", ServerAddress.check("https://llm.example.com") as AddressCheck.Allowed, null)

        assertEquals(listOf(RemoteServer(server.id, "Home", "http://192.168.1.20:8080", true, true), RemoteServer(unnamed.id, "llm.example.com", "https://llm.example.com", false, false)), store.servers.first())
        assertEquals("secret-key", store.key(server.id))
        assertNull(store.key(unnamed.id))
        val raw = dataStore.data.first().asMap().values.joinToString()
        assertTrue("the key is stored sealed, not as typed", "secret-key" !in raw)

        store.remove(server.id)
        assertEquals(listOf(unnamed.id), store.servers.first().map { it.id })
        assertNull(store.key(server.id))
    }

    @Test
    fun storedServersAreCheckedAgainOnRead() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("remote_servers")] = """[{"id":"a","name":"Old","address":"http://example.com"},{"id":"b","name":"Ok","address":"https://ok.example"}]""" }
        assertEquals(listOf("b"), store.servers.first().map { it.id })
        dataStore.edit { it[stringPreferencesKey("remote_servers")] = "not json" }
        assertEquals(emptyList<RemoteServer>(), store.servers.first())
    }

    @Test
    fun aServerIsCheckedThroughItsModelList() = runBlocking {
        val server = RemoteServer("a", "Home", "http://192.168.1.20:8080", true, true)

        assertEquals(ServerCheck.Models(listOf("qwen3-8b", "gemma-4")), client.models(server, "k"))
        assertEquals("http://192.168.1.20:8080/v1/models", requests.single().first)
        assertEquals("Bearer k", requests.single().second["Authorization"])
        client.models(server, null)
        assertNull(requests.last().second["Authorization"])

        status = 401
        assertEquals(ServerCheck.Failed("The server refused the API key."), client.models(server, "bad"))
        status = 301
        assertTrue((client.models(server, "k") as ServerCheck.Failed).reason.contains("redirects"))
        status = 500
        assertEquals(ServerCheck.Failed("The server answered with HTTP 500."), client.models(server, "k"))
        status = 200
        body = "{}"
        assertTrue((client.models(server, "k") as ServerCheck.Failed).reason.contains("OpenAI-compatible"))
        body = "<html>"
        assertTrue((client.models(server, "k") as ServerCheck.Failed).reason.contains("OpenAI-compatible"))
        body = "x".repeat(1024 * 1024 + 5)
        assertEquals(ServerCheck.Failed("The server's model list was too large."), client.models(server, "k"))
        offline = true
        assertEquals(ServerCheck.Failed("The server could not be reached."), client.models(server, "k"))
        val before = requests.size
        networkAllowed = false
        assertTrue((client.models(server, "k") as ServerCheck.Failed).reason.contains("Offline"))
        assertEquals(before, requests.size)
    }

    @Test
    fun serversAreCheckedAtStartAndTheirModelsCanBeChosen() = runBlocking {
        val server = store.add("Home", local(), null)
        val chosen = mutableListOf<Pair<RemoteServer, String>>()
        val active = kotlinx.coroutines.flow.MutableStateFlow<com.bizzeh.bruce.models.RemoteChoice?>(null)
        val viewModel = ServersViewModel(store, client, active) { chosen += it }

        val ready = withTimeout(5_000) { viewModel.state.first { it.statuses[server.id] is ServerStatus.Ready } }
        assertEquals(listOf(com.bizzeh.bruce.models.RemoteChoice(server.id, "Home", "qwen3-8b"), com.bizzeh.bruce.models.RemoteChoice(server.id, "Home", "gemma-4")), ready.choices())

        viewModel.use(ready.choices().last())
        withTimeout(5_000) { while (chosen.isEmpty()) kotlinx.coroutines.delay(10) }
        assertEquals(listOf(server to "gemma-4"), chosen)
        viewModel.use(com.bizzeh.bruce.models.RemoteChoice("gone", "Old", "m"))
        active.value = ready.choices().last()
        withTimeout(5_000) { viewModel.state.first { it.active?.model == "gemma-4" } }
        assertEquals(1, chosen.size)
    }

    @Test
    fun theViewModelAddsChecksAndRemoves() = runBlocking {
        val viewModel = ServersViewModel(store, client)
        suspend fun await(predicate: (ServersState) -> Boolean) = withTimeout(5_000) { viewModel.state.first(predicate) }

        viewModel.add("", "http://example.com", "")
        await { it.addError?.contains("local network") == true }

        viewModel.add("Home", "192.168.1.20:8080", "k")
        val ready = await { state -> state.servers.size == 1 && state.statuses.values.any { it is ServerStatus.Ready } }
        val server = ready.servers.single()
        assertEquals(ServerStatus.Ready(listOf("qwen3-8b", "gemma-4")), ready.statuses[server.id])
        assertNull(ready.addError)

        offline = true
        viewModel.check(server)
        await { it.statuses[server.id] is ServerStatus.Failed }
        viewModel.remove(server)
        await { it.servers.isEmpty() && it.statuses.isEmpty() }
        Unit
    }
}
