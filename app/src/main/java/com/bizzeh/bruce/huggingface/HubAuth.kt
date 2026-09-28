package com.bizzeh.bruce.huggingface

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Encrypts the access token at rest. The device implementation keeps its key in Android Keystore. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray
    /** Null when the data cannot be decrypted, for example after the key was removed. */
    fun decrypt(sealed: ByteArray): ByteArray?
}

data class HubAccount(val username: String, val expiresAtMillis: Long)

sealed interface SignInResult {
    data class SignedIn(val account: HubAccount) : SignInResult

    data class Failed(val error: SignInError) : SignInResult
}

enum class SignInError {
    NETWORK_DISABLED,
    OFFLINE,
    /** The redirect did not match the sign-in Bruce started: wrong state, or none pending. */
    UNEXPECTED_CALLBACK,
    /** The user declined, or Hugging Face returned an error. */
    DENIED,
    TOKEN_EXCHANGE_FAILED,
}

/** A sign-in in progress: open [authorizeUrl] in the browser, then pass the redirect to [HubAuth.complete]. */
data class PendingSignIn(val authorizeUrl: String, internal val state: String, internal val verifier: String)

/**
 * Hugging Face sign-in with OAuth 2 authorisation code + PKCE, as a public app with no client
 * secret (.learnings/hf-oauth-for-native-apps.md). The token is stored encrypted; the code
 * verifier lives only in memory, so a sign-in interrupted by process death is simply retried.
 */
class HubAuth(
    private val transport: HttpTransport,
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val networkAllowed: suspend () -> Boolean,
    private val dispatcher: CoroutineDispatcher,
    private val clientId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val baseUrl: String = "https://huggingface.co",
) {
    init {
        require(baseUrl.startsWith("https://")) { "Hub base URL must use HTTPS" }
    }

    @Volatile
    private var pending: PendingSignIn? = null

    /** The signed-in account, or null when signed out or the token has expired. */
    val account: Flow<HubAccount?> = dataStore.data.map { preferences ->
        val username = preferences[USERNAME] ?: return@map null
        val expires = preferences[EXPIRES] ?: return@map null
        HubAccount(username, expires).takeIf { expires > clock() && preferences[TOKEN] != null }
    }

    fun begin(): PendingSignIn {
        val verifier = randomToken(32)
        val state = randomToken(16)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val query = mapOf(
            "client_id" to clientId,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to SCOPES,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
        ).entries.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20") }
        return PendingSignIn("$baseUrl/oauth/authorize?$query", state, verifier).also { pending = it }
    }

    /** Handles the browser redirect: query parameters `code`/`state`, or `error`. */
    suspend fun complete(parameters: Map<String, String?>): SignInResult {
        val started = pending
        if (started == null || parameters["state"] != started.state) return SignInResult.Failed(SignInError.UNEXPECTED_CALLBACK)
        pending = null
        val code = parameters["code"]
        if (parameters["error"] != null || code.isNullOrBlank()) return SignInResult.Failed(SignInError.DENIED)
        if (!networkAllowed()) return SignInResult.Failed(SignInError.NETWORK_DISABLED)

        return try {
            withContext(dispatcher) { exchange(code, started.verifier) }
        } catch (e: IOException) {
            SignInResult.Failed(SignInError.OFFLINE)
        }
    }

    /** The bearer token for Hub requests, or null when signed out or expired. */
    suspend fun accessToken(): String? {
        val preferences = dataStore.data.first()
        val expires = preferences[EXPIRES] ?: return null
        if (expires <= clock()) return null
        val sealed = preferences[TOKEN]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return cipher.decrypt(sealed)?.toString(Charsets.UTF_8)
    }

    suspend fun signOut() {
        pending = null
        dataStore.edit {
            it.remove(TOKEN)
            it.remove(EXPIRES)
            it.remove(USERNAME)
        }
    }

    private suspend fun exchange(code: String, verifier: String): SignInResult {
        val response = transport.postForm(
            "$baseUrl/oauth/token",
            mapOf("Accept" to "application/json"),
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ),
            MAX_RESPONSE_BYTES,
        )
        if (response == null || response.status != 200) return SignInResult.Failed(SignInError.TOKEN_EXCHANGE_FAILED)
        val (token, lifetimeSeconds) = try {
            val json = JSONObject(response.body.toString(Charsets.UTF_8))
            json.getString("access_token") to json.optLong("expires_in", DEFAULT_LIFETIME_SECONDS)
        } catch (e: JSONException) {
            return SignInResult.Failed(SignInError.TOKEN_EXCHANGE_FAILED)
        }
        if (token.isBlank() || lifetimeSeconds <= 0) return SignInResult.Failed(SignInError.TOKEN_EXCHANGE_FAILED)
        val username = username(token) ?: return SignInResult.Failed(SignInError.TOKEN_EXCHANGE_FAILED)

        val account = HubAccount(username, clock() + lifetimeSeconds * 1000)
        val sealed = Base64.encodeToString(cipher.encrypt(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        dataStore.edit {
            it[TOKEN] = sealed
            it[EXPIRES] = account.expiresAtMillis
            it[USERNAME] = account.username
        }
        return SignInResult.SignedIn(account)
    }

    private fun username(token: String): String? {
        val response = transport.get("$baseUrl/oauth/userinfo", mapOf("Authorization" to "Bearer $token", "Accept" to "application/json"), MAX_RESPONSE_BYTES)
        if (response == null || response.status != 200) return null
        return try {
            val json = JSONObject(response.body.toString(Charsets.UTF_8))
            listOf("preferred_username", "name", "sub").map(json::optString).firstOrNull { it.isNotBlank() }?.take(MAX_USERNAME)
        } catch (e: JSONException) {
            null
        }
    }

    private fun randomToken(bytes: Int) = base64Url(ByteArray(bytes).also(random::nextBytes))

    private fun base64Url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        const val REDIRECT_URI = "com.bizzeh.bruce:/oauth/huggingface"
        /** Private repos and public gated repos; nothing that can write. */
        const val SCOPES = "openid profile read-repos gated-repos"
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val MAX_USERNAME = 100
        /** Hugging Face's documented default when a response omits expires_in. */
        private const val DEFAULT_LIFETIME_SECONDS = 8L * 60 * 60
        private val TOKEN = stringPreferencesKey("hf_token_sealed")
        private val EXPIRES = longPreferencesKey("hf_token_expires")
        private val USERNAME = stringPreferencesKey("hf_username")
    }
}
