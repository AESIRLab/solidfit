package com.example.solidfit.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentWebIdDataStore by preferencesDataStore(name = "recent_webids")

class RecentWebIdStore(private val context: Context) {

    private object Keys {
        val RECENTS: Preferences.Key<String> = stringPreferencesKey("recents_csv")
    }

    // newline-separated so order is preserved
    val recentWebIds: Flow<List<String>> = context.recentWebIdDataStore.data.map { prefs ->
        val raw = prefs[Keys.RECENTS].orEmpty()
        raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    suspend fun add(webIdRaw: String, maxItems: Int = 6) {
        val webId = webIdRaw.trim()
        if (webId.isBlank()) return

        context.recentWebIdDataStore.edit { prefs ->
            val current = prefs[Keys.RECENTS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val updated = (listOf(webId) + current)
                .distinct()          // de-dupe, keeps first occurrence
                .take(maxItems)

            prefs[Keys.RECENTS] = updated.joinToString("\n")
        }
    }

    suspend fun remove(webIdRaw: String) {
        val webId = webIdRaw.trim()
        if (webId.isBlank()) return

        context.recentWebIdDataStore.edit { prefs ->
            val current = prefs[Keys.RECENTS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            prefs[Keys.RECENTS] = current.filterNot { it == webId }.joinToString("\n")
        }
    }

    suspend fun clear() {
        context.recentWebIdDataStore.edit { prefs ->
            prefs[Keys.RECENTS] = ""
        }
    }
}
