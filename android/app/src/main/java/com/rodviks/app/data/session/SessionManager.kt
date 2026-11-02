package com.rodviks.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rodviks_session")

data class SessionData(
    val token: String?,
    val userId: String?,
    val baseUrl: String?
)

class SessionManager(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    val sessionFlow: Flow<SessionData> = context.dataStore.data.map { prefs ->
        SessionData(
            token = prefs[Keys.TOKEN],
            userId = prefs[Keys.USER_ID],
            baseUrl = prefs[Keys.BASE_URL]
        )
    }

    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { it[Keys.BASE_URL] }

    suspend fun saveAuth(token: String, userId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
        }
    }

    suspend fun saveBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = normalized
        }
    }

    /**
     * Accepts "host", "host:3000", "http://host" etc. and stores a full
     * "scheme://host:port" URL, so the interceptor never falls back to port 80
     * when the user typed an address without a port.
     */
    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return DEFAULT_BASE_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        val hasPort = Regex(":[0-9]+$").containsMatchIn(url.substringAfter("://"))
        if (!hasPort) {
            url = if (url.startsWith("https://")) "$url:443" else "$url:3000"
        }
        return url
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USER_ID)
        }
    }

    suspend fun currentToken(): String? = sessionFlow.first().token
    suspend fun currentUserId(): String? = sessionFlow.first().userId
    suspend fun currentBaseUrl(): String? = sessionFlow.first().baseUrl

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"
    }
}
