package com.nexy.client.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import java.io.File
import java.security.KeyStore
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthTokenManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AuthTokenManager"
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val SAVED_EMAIL_KEY = stringPreferencesKey("saved_email")
        private val REMEMBER_ME_KEY = stringPreferencesKey("remember_me")
        private val BACKGROUND_SERVICE_ENABLED_KEY = booleanPreferencesKey("background_service_enabled")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        
        private const val ENCRYPTED_PREFS_NAME = "secure_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }
    
    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            createEncryptedPrefsInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, clearing corrupted data", e)
            clearCorruptedEncryptedPrefs()
            try {
                createEncryptedPrefsInternal()
            } catch (e2: Exception) {
                Log.e(TAG, "Still failed after clearing, using fallback", e2)
                // Fallback to regular SharedPreferences (less secure but won't crash)
                context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }
    
    private fun createEncryptedPrefsInternal(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private fun clearCorruptedEncryptedPrefs() {
        try {
            // Clear the encrypted shared preferences file
            val prefsFile = File(context.filesDir.parent, "shared_prefs/$ENCRYPTED_PREFS_NAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
                Log.d(TAG, "Deleted corrupted prefs file")
            }
            
            // Clear the MasterKey from Android Keystore
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MASTER_KEY_ALIAS)
                    Log.d(TAG, "Deleted corrupted master key")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete master key", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing corrupted prefs", e)
        }
    }
    
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tokens", e)
        }
    }
    
    suspend fun getAccessToken(): String? {
        return try {
            encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }
    
    suspend fun getRefreshToken(): String? {
        return try {
            encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get refresh token", e)
            null
        }
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
        try {
            encryptedPrefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear tokens from encrypted prefs", e)
        }
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return try {
            getAccessToken() != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check logged in status", e)
            false
        }
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
