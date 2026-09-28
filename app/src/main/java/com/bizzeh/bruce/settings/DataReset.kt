package com.bizzeh.bruce.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.bizzeh.bruce.models.ActiveModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/** Clears everything Bruce stores: the loaded model, model files, saved chats, skill states, grants, settings and cached files. */
class DataReset(
    private val activeModel: ActiveModel,
    private val settings: DataStore<Preferences>,
    private val modelsDir: File,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    /** Clears the Room databases (saved chats, skill states) and releases file and folder grants. */
    private val clearDatabases: suspend () -> Unit,
) {
    suspend fun clearAll() {
        activeModel.unload()
        clearDatabases()
        withContext(ioDispatcher) {
            modelsDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
            cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
        }
        // DataStore owns its file while in use, so its contents are cleared rather than the file deleted.
        settings.edit { it.clear() }
        activeModel.refresh()
    }
}
