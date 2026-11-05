package dev.hashir.orbittv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(name = "orbit_prefs")

/** Everything the user has chosen: pins, history and the display toggles. */
class UserPrefs(private val context: Context) {

    private val store get() = context.prefsStore

    private val safe: Flow<Preferences>
        get() = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val favorites: Flow<Set<String>> = safe.map { it[KEY_FAVORITES] ?: emptySet() }

    /** Most-recently watched first. */
    val recents: Flow<List<String>> = safe.map { prefs ->
        prefs[KEY_RECENTS]?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
    }

    val hideOffline: Flow<Boolean> = safe.map { it[KEY_HIDE_OFFLINE] ?: true }

    val showAdult: Flow<Boolean> = safe.map { it[KEY_SHOW_ADULT] ?: false }

    val homeCountry: Flow<String> = safe.map { it[KEY_HOME_COUNTRY] ?: "" }

    suspend fun toggleFavorite(channelId: String) {
        store.edit { prefs ->
            val current = prefs[KEY_FAVORITES] ?: emptySet()
            prefs[KEY_FAVORITES] = if (channelId in current) current - channelId else current + channelId
        }
    }

    suspend fun noteWatched(channelId: String) {
        store.edit { prefs ->
            val current = prefs[KEY_RECENTS]?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(channelId) + current.filter { it != channelId }).take(MAX_RECENTS)
            prefs[KEY_RECENTS] = updated.joinToString(SEP)
        }
    }

    suspend fun clearRecents() {
        store.edit { it[KEY_RECENTS] = "" }
    }

    suspend fun setHideOffline(value: Boolean) {
        store.edit { it[KEY_HIDE_OFFLINE] = value }
    }

    suspend fun setShowAdult(value: Boolean) {
        store.edit { it[KEY_SHOW_ADULT] = value }
    }

    suspend fun setHomeCountry(code: String) {
        store.edit { it[KEY_HOME_COUNTRY] = code }
    }

    private companion object {
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        val KEY_RECENTS = stringPreferencesKey("recents")
        val KEY_HIDE_OFFLINE = booleanPreferencesKey("hide_offline")
        val KEY_SHOW_ADULT = booleanPreferencesKey("show_adult")
        val KEY_HOME_COUNTRY = stringPreferencesKey("home_country")
        const val MAX_RECENTS = 30
        const val SEP = "\u0001"  // unit separator; cannot occur in a channel id
    }
}
