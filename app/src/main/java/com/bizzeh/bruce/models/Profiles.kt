package com.bizzeh.bruce.models

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** The model a profile uses: a file on the phone, or a model on one of the user's servers. */
sealed interface ProfileModel {
    data class Local(val fileName: String) : ProfileModel

    data class Remote(val serverId: String, val model: String) : ProfileModel
}

/** A named model choice (TASK-098), such as Fast or Coding, picked per chat. */
data class Profile(val id: String, val name: String, val model: ProfileModel)

/**
 * Model profiles, kept in settings. Chosen per chat (owner, 2026-09-30); no automatic routing, as
 * switching a phone model means reloading it.
 */
class ProfileStore(private val dataStore: DataStore<Preferences>) {
    val profiles: Flow<List<Profile>> = dataStore.data.map { parse(it[PROFILES]) }

    suspend fun profile(id: String): Profile? = profiles.first().firstOrNull { it.id == id }

    suspend fun add(name: String, model: ProfileModel): Profile {
        val profile = Profile(UUID.randomUUID().toString(), name.trim().take(MAX_NAME), model)
        dataStore.edit { it[PROFILES] = write(parse(it[PROFILES]) + profile) }
        return profile
    }

    suspend fun remove(id: String) {
        dataStore.edit { it[PROFILES] = write(parse(it[PROFILES]).filterNot { p -> p.id == id }) }
    }

    private fun write(profiles: List<Profile>): String = JSONArray(profiles.map { profile ->
        JSONObject().put("id", profile.id).put("name", profile.name).apply {
            when (val model = profile.model) {
                is ProfileModel.Local -> put("file", model.fileName)
                is ProfileModel.Remote -> put("server", model.serverId).put("model", model.model)
            }
        }
    }).toString()

    /** Anything unreadable is dropped rather than guessed at. */
    private fun parse(json: String?): List<Profile> = try {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val model = when {
                o.has("file") -> ProfileModel.Local(o.getString("file"))
                o.has("server") -> ProfileModel.Remote(o.getString("server"), o.getString("model"))
                else -> return@mapNotNull null
            }
            Profile(o.getString("id"), o.getString("name"), model)
        }
    } catch (e: JSONException) {
        emptyList()
    }

    companion object {
        const val MAX_NAME = 40
        /** Names offered when saving a profile (plan.md). */
        val SUGGESTED = listOf("Main", "Fast", "Coding", "Vision")
        private val PROFILES = stringPreferencesKey("model_profiles")
    }
}
