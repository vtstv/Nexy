package com.nexy.client.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

// Use the same name as in SettingsViewModel to access the same preferences
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val PUSH_NOTIFICATIONS_KEY = booleanPreferencesKey("push_notifications_enabled")
    }

    suspend fun isPushNotificationsEnabled(): Boolean {
        return context.settingsDataStore.data.map { prefs ->
            prefs[PUSH_NOTIFICATIONS_KEY] ?: true
        }.first()
    }
}
