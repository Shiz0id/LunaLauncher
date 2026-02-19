package com.lunasysman.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lunasysman.launcher.core.justtype.model.JustTypeNotificationsOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.justTypePrefsDataStore by preferencesDataStore(name = "just_type_prefs")

class JustTypePrefs(
    private val context: Context,
) {
    val defaultSearchProviderId: Flow<String?> =
        context.justTypePrefsDataStore.data.map { prefs -> prefs[KEY_DEFAULT_SEARCH_PROVIDER_ID] }

    val glassOpacity: Flow<Float> =
        context.justTypePrefsDataStore.data.map { prefs ->
            (prefs[KEY_GLASS_OPACITY] ?: DEFAULT_GLASS_OPACITY).coerceIn(0f, 1f)
        }

    val notificationsOptions: Flow<JustTypeNotificationsOptions> =
        context.justTypePrefsDataStore.data.map { prefs ->
            JustTypeNotificationsOptions(
                maxResults = (prefs[KEY_NOTIFICATIONS_MAX_RESULTS] ?: DEFAULT_NOTIFICATIONS_MAX_RESULTS)
                    .coerceIn(1, 25),
                matchText = prefs[KEY_NOTIFICATIONS_MATCH_TEXT] ?: DEFAULT_NOTIFICATIONS_MATCH_TEXT,
                matchNames = prefs[KEY_NOTIFICATIONS_MATCH_NAMES] ?: DEFAULT_NOTIFICATIONS_MATCH_NAMES,
                showActions = prefs[KEY_NOTIFICATIONS_SHOW_ACTIONS] ?: DEFAULT_NOTIFICATIONS_SHOW_ACTIONS,
            )
        }

    suspend fun setGlassOpacity(value: Float) {
        context.justTypePrefsDataStore.edit { prefs ->
            prefs[KEY_GLASS_OPACITY] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setDefaultSearchProviderId(id: String) {
        context.justTypePrefsDataStore.edit { prefs -> prefs[KEY_DEFAULT_SEARCH_PROVIDER_ID] = id }
    }

    suspend fun setNotificationsMaxResults(value: Int) {
        context.justTypePrefsDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_MAX_RESULTS] = value.coerceIn(1, 25)
        }
    }

    suspend fun setNotificationsMatchText(enabled: Boolean) {
        context.justTypePrefsDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_MATCH_TEXT] = enabled
        }
    }

    suspend fun setNotificationsMatchNames(enabled: Boolean) {
        context.justTypePrefsDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_MATCH_NAMES] = enabled
        }
    }

    suspend fun setNotificationsShowActions(enabled: Boolean) {
        context.justTypePrefsDataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_SHOW_ACTIONS] = enabled
        }
    }

    companion object {
        private val KEY_GLASS_OPACITY: Preferences.Key<Float> = floatPreferencesKey("justTypeGlassOpacity")
        private val KEY_DEFAULT_SEARCH_PROVIDER_ID: Preferences.Key<String> = stringPreferencesKey("defaultSearchProviderId")
        private val KEY_NOTIFICATIONS_MAX_RESULTS: Preferences.Key<Int> = intPreferencesKey("notificationsMaxResults")
        private val KEY_NOTIFICATIONS_MATCH_TEXT: Preferences.Key<Boolean> = booleanPreferencesKey("notificationsMatchText")
        private val KEY_NOTIFICATIONS_MATCH_NAMES: Preferences.Key<Boolean> = booleanPreferencesKey("notificationsMatchNames")
        private val KEY_NOTIFICATIONS_SHOW_ACTIONS: Preferences.Key<Boolean> = booleanPreferencesKey("notificationsShowActions")

        private const val DEFAULT_GLASS_OPACITY = 0.5f
        private const val DEFAULT_NOTIFICATIONS_MAX_RESULTS = 5
        private const val DEFAULT_NOTIFICATIONS_MATCH_TEXT = true
        private const val DEFAULT_NOTIFICATIONS_MATCH_NAMES = true
        private const val DEFAULT_NOTIFICATIONS_SHOW_ACTIONS = true
    }
}
