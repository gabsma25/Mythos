package com.mythos.reader.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mythos.reader.model.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "reader_preferences")

class ReaderPreferencesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("epub")

    val preferences: Flow<ReaderPreferences> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<ReaderPreferences>(it) }.getOrNull() }
            ?: ReaderPreferences()
    }

    suspend fun current(): ReaderPreferences = preferences.first()

    suspend fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { json.decodeFromString<ReaderPreferences>(it) }.getOrNull() }
                ?: ReaderPreferences()
            prefs[key] = json.encodeToString(transform(current))
        }
    }
}
