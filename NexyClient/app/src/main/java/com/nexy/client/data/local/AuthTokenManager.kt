package com.nexy.client.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthTokenManager(private val context: Context) {
    
    companion object {
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val SAVED_EMAIL_KEY = stringPreferencesKey("saved_email")
        private val REMEMBER_ME_KEY = stringPreferencesKey("remember_me")
        private val BACKGROUND_SERVICE_ENABLED_KEY = booleanPreferencesKey("background_service_enabled")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        
        private const val ENCRYPTED_PREFS_NAME = "secure_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }
    
    suspend fun getAccessToken(): String? {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    suspend fun getRefreshToken(): String? {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
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
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
    
    suspend fun saveCredentials(email: String) {
        context.dataStore.edit { prefs ->
            prefs[SAVED_EMAIL_KEY] = email
            prefs[REMEMBER_ME_KEY] = "true"
        }
    }
    
    suspend fun getSavedEmail(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[SAVED_EMAIL_KEY]
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
            prefs.remove(REMEMBER_ME_KEY)
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BACKGROUND_SERVICE_ENABLED_KEY] = enabled
        }
    }

    fun getBackgroundServiceEnabledFlow() = context.dataStore.data.map { prefs ->
        prefs[BACKGROUND_SERVICE_ENABLED_KEY] ?: false
    }
    
    suspend fun getBackgroundServiceEnabled(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[BACKGROUND_SERVICE_ENABLED_KEY] ?: false
        }.first()
    }

    suspend fun getDeviceId(): String {
        return context.dataStore.data.map { prefs ->
            prefs[DEVICE_ID_KEY]
        }.first() ?: run {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                "unknown"
            }
            
            val model = Build.MODEL.replace(" ", "-")
            val uniqueId = UUID.randomUUID().toString()
            val deviceId = "Android-$model-$androidId-$uniqueId"
            
            context.dataStore.edit { prefs ->
                prefs[DEVICE_ID_KEY] = deviceId
            }
            
            deviceId
        }
    }
}
