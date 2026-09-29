package com.bizzeh.bruce.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bizzeh.bruce.huggingface.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64
import java.util.UUID

/** A model server the user added (TASK-075). Its API key, if any, is kept apart and sealed. */
data class RemoteServer(val id: String, val name: String, val address: String, val plain: Boolean, val hasKey: Boolean)

/**
 * The user's model servers, in settings. Each API key is sealed with the Android Keystore under
 * the server's id, never logged or shown again, and sent only to that server.
 */
class RemoteServerStore(private val dataStore: DataStore<Preferences>, private val cipher: TokenCipher) {
    val servers: Flow<List<RemoteServer>> = dataStore.data.map { preferences -> parse(preferences[SERVERS]) }

    suspend fun add(name: String, address: AddressCheck.Allowed, key: String?): RemoteServer {
        val server = RemoteServer(UUID.randomUUID().toString(), name.trim().take(MAX_NAME).ifEmpty { address.base.substringAfter("://") }, address.base, address.plain, !key.isNullOrBlank())
        dataStore.edit { preferences ->
            preferences[SERVERS] = write(parse(preferences[SERVERS]) + server)
            key?.trim()?.takeIf { it.isNotEmpty() }?.let { preferences[keyFor(server.id)] = Base64.getEncoder().encodeToString(cipher.encrypt(it.toByteArray(Charsets.UTF_8))) }
        }
        return server
    }

    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            preferences[SERVERS] = write(parse(preferences[SERVERS]).filterNot { it.id == id })
            preferences.remove(keyFor(id))
        }
    }

    suspend fun key(id: String): String? {
        val sealed = dataStore.data.first()[keyFor(id)]?.let { Base64.getDecoder().decode(it) } ?: return null
        return cipher.decrypt(sealed)?.toString(Charsets.UTF_8)
    }

    private fun parse(json: String?): List<RemoteServer> = try {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            // A stored address is checked again, so a changed rule applies to servers added before it.
            val address = ServerAddress.check(item.optString("address")) as? AddressCheck.Allowed ?: return@mapNotNull null
            RemoteServer(item.optString("id"), item.optString("name"), address.base, address.plain, item.optBoolean("hasKey"))
        }
    } catch (e: JSONException) {
        emptyList()
    }

    private fun write(servers: List<RemoteServer>): String = JSONArray(
        servers.map { JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("hasKey", it.hasKey) },
    ).toString()

    private fun keyFor(id: String) = stringPreferencesKey("remote_server_key_$id")

    private companion object {
        val SERVERS = stringPreferencesKey("remote_servers")
        const val MAX_NAME = 60
    }
}
