package com.bizzeh.bruce.huggingface

import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class HubAuthTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val transport = OAuthServer()
    private var allowed = true
    private var now = 1_000_000L
    private var stores = 0

    private fun TestScope.auth() = HubAuth(
        transport = transport,
        dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(temp.root, "s${stores++}.preferences_pb") },
        cipher = ReversingCipher,
        networkAllowed = { allowed },
        dispatcher = dispatcher,
        clientId = "client-123",
        clock = { now },
    )

    private fun callback(pending: PendingSignIn, code: String? = "the-code") =
        mapOf("state" to pending.state, "code" to code)

    @Test
    fun authorizeUrlUsesPkceAndTheRegisteredRedirect() = runTest(dispatcher) {
        val pending = auth().begin()
        val uri = Uri.parse(pending.authorizeUrl)

        assertEquals("https://huggingface.co/oauth/authorize", pending.authorizeUrl.substringBefore('?'))
        assertEquals("client-123", uri.getQueryParameter("client_id"))
        assertEquals("com.bizzeh.bruce:/oauth/huggingface", uri.getQueryParameter("redirect_uri"))
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertEquals("openid profile read-repos gated-repos", uri.getQueryParameter("scope"))
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
        assertEquals(pending.state, uri.getQueryParameter("state"))
        val expectedChallenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        assertEquals(expectedChallenge, uri.getQueryParameter("code_challenge"))
        assertTrue(pending.verifier.length >= 43)
    }

    @Test
    fun eachSignInHasFreshSecrets() = runTest(dispatcher) {
        val auth = auth()
        val first = auth.begin()
        val second = auth.begin()

        assertNotEquals(first.state, second.state)
        assertNotEquals(first.verifier, second.verifier)
    }

    @Test
    fun successfulSignInStoresTheTokenEncrypted() = runTest(dispatcher) {
        val auth = auth()
        val pending = auth.begin()

        val result = auth.complete(callback(pending))

        assertEquals(SignInResult.SignedIn(HubAccount("bruce-owner", now + 604_800_000L)), result)
        assertEquals("secret-token", auth.accessToken())
        assertEquals(HubAccount("bruce-owner", now + 604_800_000L), auth.account.first())
        val form = transport.forms.single()
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("the-code", form["code"])
        assertEquals(pending.verifier, form["code_verifier"])
        assertEquals("client-123", form["client_id"])
        assertNull("no client secret is ever sent", form["client_secret"])
        assertEquals("Bearer secret-token", transport.userinfoAuth)
    }

    @Test
    fun expiredTokenIsTreatedAsSignedOut() = runTest(dispatcher) {
        val auth = auth()
        auth.complete(callback(auth.begin()))

        now += 604_800_000L

        assertNull(auth.accessToken())
        assertNull(auth.account.first())
    }

    @Test
    fun signOutForgetsTheToken() = runTest(dispatcher) {
        val auth = auth()
        auth.complete(callback(auth.begin()))

        auth.signOut()

        assertNull(auth.accessToken())
        assertNull(auth.account.first())
    }

    @Test
    fun callbacksThatDoNotMatchTheSignInAreRejected() = runTest(dispatcher) {
        val auth = auth()
        assertEquals(SignInResult.Failed(SignInError.UNEXPECTED_CALLBACK), auth.complete(mapOf("state" to "x", "code" to "c")))

        val pending = auth.begin()
        assertEquals(SignInResult.Failed(SignInError.UNEXPECTED_CALLBACK), auth.complete(mapOf("state" to "forged", "code" to "c")))
        auth.complete(callback(pending))
        assertEquals("the verifier is single use", SignInResult.Failed(SignInError.UNEXPECTED_CALLBACK), auth.complete(callback(pending)))
        assertTrue(transport.forms.size == 1)
    }

    @Test
    fun deniedOrMissingCode() = runTest(dispatcher) {
        val auth = auth()
        val denied = auth.begin()
        assertEquals(SignInResult.Failed(SignInError.DENIED), auth.complete(mapOf("state" to denied.state, "error" to "access_denied")))
        assertEquals(SignInResult.Failed(SignInError.DENIED), auth.complete(callback(auth.begin(), code = "")))
    }

    @Test
    fun networkModeIsRespected() = runTest(dispatcher) {
        val auth = auth()
        allowed = false

        assertEquals(SignInResult.Failed(SignInError.NETWORK_DISABLED), auth.complete(callback(auth.begin())))
        assertTrue(transport.forms.isEmpty())
    }

    @Test
    fun exchangeFailures() = runTest(dispatcher) {
        val cases = listOf<OAuthServer.() -> Unit>(
            { tokenStatus = 400 },
            { tokenBody = "not json" },
            { tokenBody = """{"access_token":""}""" },
            { tokenBody = """{"access_token":"t","expires_in":0}""" },
            { userinfoStatus = 401 },
            { userinfoBody = "{}" },
            { userinfoBody = "not json" },
            { tooLarge = true },
        )
        for (setUp in cases) {
            transport.reset()
            transport.setUp()
            val auth = auth()
            assertEquals(SignInResult.Failed(SignInError.TOKEN_EXCHANGE_FAILED), auth.complete(callback(auth.begin())))
            assertNull(auth.accessToken())
        }
    }

    @Test
    fun networkFailureDuringExchange() = runTest(dispatcher) {
        transport.fail = true
        val auth = auth()

        assertEquals(SignInResult.Failed(SignInError.OFFLINE), auth.complete(callback(auth.begin())))
    }

    @Test
    fun undecryptableTokenIsNoToken() = runTest(dispatcher) {
        val auth = HubAuth(
            transport, PreferenceDataStoreFactory.create(scope = backgroundScope) { temp.newFile("b.preferences_pb").also { it.delete() } },
            object : TokenCipher {
                override fun encrypt(plain: ByteArray) = plain
                override fun decrypt(sealed: ByteArray): ByteArray? = null
            },
            { true }, dispatcher, "client-123", { now },
        )
        auth.complete(mapOf("state" to auth.begin().state, "code" to "c"))

        assertNull(auth.accessToken())
    }

    @Test(expected = IllegalArgumentException::class)
    fun httpsOnly() = runTest(dispatcher) {
        HubAuth(transport, PreferenceDataStoreFactory.create(scope = backgroundScope) { temp.newFile("c.preferences_pb").also { it.delete() } }, ReversingCipher, { true }, dispatcher, "c", baseUrl = "http://h")
    }

    /** Reverses the bytes, so tests can see the stored value is not the plain token. */
    private object ReversingCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(sealed: ByteArray) = sealed.reversedArray()
    }

    private class OAuthServer : HttpTransport {
        val forms = mutableListOf<Map<String, String>>()
        var userinfoAuth: String? = null
        var tokenStatus = 200
        var tokenBody = """{"access_token":"secret-token","token_type":"bearer","expires_in":604800}"""
        var userinfoStatus = 200
        var userinfoBody = """{"sub":"123","preferred_username":"bruce-owner"}"""
        var tooLarge = false
        var fail = false

        fun reset() {
            forms.clear(); tokenStatus = 200; userinfoStatus = 200; tooLarge = false; fail = false
            tokenBody = """{"access_token":"secret-token","token_type":"bearer","expires_in":604800}"""
            userinfoBody = """{"sub":"123","preferred_username":"bruce-owner"}"""
        }

        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse? {
            if (fail) throw IOException("offline")
            assertEquals("https://huggingface.co/oauth/token", url)
            forms += form
            return if (tooLarge) null else HttpResponse(tokenStatus, emptyMap(), tokenBody.toByteArray())
        }

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse {
            assertEquals("https://huggingface.co/oauth/userinfo", url)
            userinfoAuth = headers["Authorization"]
            return HttpResponse(userinfoStatus, emptyMap(), userinfoBody.toByteArray())
        }

        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("not used")
    }
}
