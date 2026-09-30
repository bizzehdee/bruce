package com.bizzeh.bruce.models

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class ProfileStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun profilesAreAddedFoundAndRemoved() = runTest {
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(temp.root, "p.preferences_pb") }
        val store = ProfileStore(data)

        val fast = store.add("  Fast  ", ProfileModel.Local("qwen.gguf"))
        val coding = store.add("C".repeat(50), ProfileModel.Remote("s1", "qwen3-coder"))

        assertEquals(listOf("Fast", "C".repeat(40)), store.profiles.first().map { it.name })
        assertEquals(ProfileModel.Remote("s1", "qwen3-coder"), store.profile(coding.id)!!.model)
        store.remove(fast.id)
        assertNull(store.profile(fast.id))
        assertEquals(1, store.profiles.first().size)

        data.edit { it[stringPreferencesKey("model_profiles")] = "[{\"id\":\"x\",\"name\":\"odd\"}, 3]" }
        assertEquals(emptyList<Profile>(), store.profiles.first())
        data.edit { it[stringPreferencesKey("model_profiles")] = "not json" }
        assertEquals(emptyList<Profile>(), store.profiles.first())
    }
}
