package com.nexy.client.data.local

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthTokenManager(private val context: Context) {
    
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val SAVED_EMAIL_KEY = stringPreferencesKey("saved_email")
        private val SAVED_PASSWORD_KEY = stringPreferencesKey("saved_password")
        private val REMEMBER_ME_KEY = stringPreferencesKey("remember_me")
        private val BACKGROUND_SERVICE_ENABLED_KEY = booleanPreferencesKey("background_service_enabled")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }
    
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }
    
    suspend fun getAccessToken(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[ACCESS_TOKEN_KEY]
        }.first()
    }
    
    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[REFRESH_TOKEN_KEY]
        }.first()
    }
    
    suspend fun saveUserId(userId: Int) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId.toString()
        }
    }
    
    suspend fun getUserId(): Int? {
        return context.dataStore.data.map { prefs ->
            prefs[USER_ID_KEY]?.toIntOrNull()
        }.first()
    }
    
    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
    
    suspend fun saveCredentials(email: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[SAVED_EMAIL_KEY] = email
            prefs[SAVED_PASSWORD_KEY] = password
            prefs[REMEMBER_ME_KEY] = "true"
        }
    }
    
    suspend fun getSavedEmail(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[SAVED_EMAIL_KEY]
        }.first()
    }
    
    suspend fun getSavedPassword(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[SAVED_PASSWORD_KEY]
        }.first()
    }
    
    suspend fun isRememberMeEnabled(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[REMEMBER_ME_KEY] == "true"
        }.first() ?: false
    }
    
    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(SAVED_EMAIL_KEY)
            prefs.remove(SAVED_PASSWORD_KEY)
            prefs.remove(REMEMBER_ME_KEY)
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BACKGROUND_SERVICE_ENABLED_KEY] = enabled
        }
    }

    fun getBackgroundServiceEnabledFlow() = context.dataStore.data.map { prefs ->
        prefs[BACKGROUND_SERVICE_ENABLED_KEY] ?: false // Default to false
    }
    
    suspend fun getBackgroundServiceEnabled(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[BACKGROUND_SERVICE_ENABLED_KEY] ?: false
        }.first()
    }

    /**
     * Get or generate a unique device ID for this app installation.
     * Format: "Android-{MODEL}-{ANDROID_ID}-{UUID}"
     * Example: "Android-SM-G991B-a1b2c3d4e5f6-12345678-1234-5678-90ab-cdef12345678"
     */
    suspend fun getDeviceId(): String {
        return context.dataStore.data.map { prefs ->
            prefs[DEVICE_ID_KEY]
        }.first() ?: run {
            // Generate new device ID
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                "unknown"
            }
            
            val model = Build.MODEL.replace(" ", "-")
            val uniqueId = UUID.randomUUID().toString()
            val deviceId = "Android-$model-$androidId-$uniqueId"
            
            // Save it
            context.dataStore.edit { prefs ->
                prefs[DEVICE_ID_KEY] = deviceId
            }
            
            deviceId
        }
    }
}
