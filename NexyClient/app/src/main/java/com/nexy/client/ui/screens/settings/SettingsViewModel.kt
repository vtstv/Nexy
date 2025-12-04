package com.nexy.client.ui.screens.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.settingsDataStore
import com.nexy.client.data.models.UserSession
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.ui.screens.chat.handlers.ChatStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// Removed private extension, using the one from SettingsManager.kt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: AuthTokenManager,
    private val userRepository: UserRepository,
    private val stateManager: ChatStateManager,
    private val apiService: NexyApiService
) : ViewModel() {

    private val PIN_CODE_KEY = stringPreferencesKey("pin_code")
    private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
    private val CACHE_MAX_SIZE_KEY = longPreferencesKey("cache_max_size") // in bytes
    private val CACHE_MAX_AGE_KEY = longPreferencesKey("cache_max_age") // in milliseconds
    
    private val PUSH_NOTIFICATIONS_KEY = booleanPreferencesKey("push_notifications_enabled")
    private val NOTIFICATION_SOUND_KEY = booleanPreferencesKey("notification_sound_enabled")
    private val NOTIFICATION_SOUND_URI_KEY = stringPreferencesKey("notification_sound_uri")
    private val NOTIFICATION_VIBRATION_KEY = booleanPreferencesKey("notification_vibration_enabled")
    private val VOICE_MESSAGES_ENABLED_KEY = booleanPreferencesKey("voice_messages_enabled")

    private val _pinCode = MutableStateFlow<String?>(null)
    val pinCode: StateFlow<String?> = _pinCode.asStateFlow()
    
    private val _notificationSoundUri = MutableStateFlow<String?>(null)
    val notificationSoundUri: StateFlow<String?> = _notificationSoundUri.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    private val _cacheMaxSize = MutableStateFlow(1024L * 1024L * 100L) // Default 100MB
    val cacheMaxSize: StateFlow<Long> = _cacheMaxSize.asStateFlow()

    private val _cacheMaxAge = MutableStateFlow(1000L * 60L * 60L * 24L * 7L) // Default 7 days
    val cacheMaxAge: StateFlow<Long> = _cacheMaxAge.asStateFlow()
    
    private val _pushNotificationsEnabled = MutableStateFlow(true)
    val pushNotificationsEnabled: StateFlow<Boolean> = _pushNotificationsEnabled.asStateFlow()
    
    private val _notificationSoundEnabled = MutableStateFlow(true)
    val notificationSoundEnabled: StateFlow<Boolean> = _notificationSoundEnabled.asStateFlow()
    
    private val _notificationVibrationEnabled = MutableStateFlow(true)
    val notificationVibrationEnabled: StateFlow<Boolean> = _notificationVibrationEnabled.asStateFlow()
    
    private val _voiceMessagesEnabled = MutableStateFlow(true)
    val voiceMessagesEnabled: StateFlow<Boolean> = _voiceMessagesEnabled.asStateFlow()

    private val _isBackgroundServiceEnabled = MutableStateFlow(true)
    val isBackgroundServiceEnabled: StateFlow<Boolean> = _isBackgroundServiceEnabled.asStateFlow()

    private val _readReceiptsEnabled = MutableStateFlow(true)
    val readReceiptsEnabled: StateFlow<Boolean> = _readReceiptsEnabled.asStateFlow()

    private val _typingIndicatorsEnabled = MutableStateFlow(true)
    val typingIndicatorsEnabled: StateFlow<Boolean> = _typingIndicatorsEnabled.asStateFlow()

    private val _sessions = MutableStateFlow<List<UserSession>>(emptyList())
    val sessions: StateFlow<List<UserSession>> = _sessions.asStateFlow()

    private val _isLoadingSessions = MutableStateFlow(false)
    val isLoadingSessions: StateFlow<Boolean> = _isLoadingSessions.asStateFlow()

    init {
        loadSettings()
        calculateCacheSize()
        loadUserProfile()
        
        viewModelScope.launch {
            tokenManager.getBackgroundServiceEnabledFlow().collect { enabled ->
                _isBackgroundServiceEnabled.value = enabled
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val userId = stateManager.loadCurrentUserId()
            if (userId != null && userId > 0) {
                userRepository.getUserById(userId).onSuccess { user ->
                    _readReceiptsEnabled.value = user.readReceiptsEnabled
                    _typingIndicatorsEnabled.value = user.typingIndicatorsEnabled
                }
            }
        }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        _readReceiptsEnabled.value = enabled
        viewModelScope.launch {
            val userId = stateManager.loadCurrentUserId()
            if (userId != null && userId > 0) {
                userRepository.getUserById(userId).onSuccess { user ->
                     userRepository.updateProfile(
                         displayName = user.displayName ?: "",
                         bio = user.bio ?: "",
                         avatarUrl = user.avatarUrl,
                         email = user.email,
                         password = null,
                         readReceiptsEnabled = enabled,
                         typingIndicatorsEnabled = null
                     )
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            context.settingsDataStore.data.collect { preferences ->
                _pinCode.value = preferences[PIN_CODE_KEY]
                _isBiometricEnabled.value = preferences[BIOMETRIC_ENABLED_KEY] ?: false
                _cacheMaxSize.value = preferences[CACHE_MAX_SIZE_KEY] ?: (1024L * 1024L * 100L)
                _cacheMaxAge.value = preferences[CACHE_MAX_AGE_KEY] ?: (1000L * 60L * 60L * 24L * 7L)
                
                _pushNotificationsEnabled.value = preferences[PUSH_NOTIFICATIONS_KEY] ?: true
                _notificationSoundEnabled.value = preferences[NOTIFICATION_SOUND_KEY] ?: true
                _notificationSoundUri.value = preferences[NOTIFICATION_SOUND_URI_KEY]
                _notificationVibrationEnabled.value = preferences[NOTIFICATION_VIBRATION_KEY] ?: true
                _voiceMessagesEnabled.value = preferences[VOICE_MESSAGES_ENABLED_KEY] ?: true
            }
        }
    }

    fun setPinCode(pin: String?) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                if (pin == null) {
                    preferences.remove(PIN_CODE_KEY)
                } else {
                    preferences[PIN_CODE_KEY] = pin
                }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[BIOMETRIC_ENABLED_KEY] = enabled
            }
        }
    }

    fun setCacheMaxSize(size: Long) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[CACHE_MAX_SIZE_KEY] = size
            }
        }
    }

    fun setCacheMaxAge(age: Long) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[CACHE_MAX_AGE_KEY] = age
            }
        }
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[PUSH_NOTIFICATIONS_KEY] = enabled
            }
        }
    }
    
    fun setNotificationSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[NOTIFICATION_SOUND_KEY] = enabled
            }
        }
    }
    
    fun setNotificationVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[NOTIFICATION_VIBRATION_KEY] = enabled
            }
        }
    }
    
    fun setVoiceMessagesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                preferences[VOICE_MESSAGES_ENABLED_KEY] = enabled
            }
        }
    }

    fun setNotificationSoundUri(uri: String?) {
        viewModelScope.launch {
            context.settingsDataStore.edit { preferences ->
                if (uri == null) {
                    preferences.remove(NOTIFICATION_SOUND_URI_KEY)
                } else {
                    preferences[NOTIFICATION_SOUND_URI_KEY] = uri
                }
            }
        }
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tokenManager.setBackgroundServiceEnabled(enabled)
        }
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val cacheDir = context.cacheDir
            _cacheSize.value = getDirSize(cacheDir)
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    fun clearCache() {
        viewModelScope.launch {
            deleteDir(context.cacheDir)
            calculateCacheSize()
        }
    }

    private fun deleteDir(dir: File): Boolean {
        if (dir.isDirectory) {
            val children = dir.list()
            children?.forEach { child ->
                val success = deleteDir(File(dir, child))
                if (!success) return false
            }
        }
        return dir.delete()
    }

    fun cleanOldCache() {
        viewModelScope.launch {
            val maxAge = _cacheMaxAge.value
            val now = System.currentTimeMillis()
            val cacheDir = context.cacheDir
            
            cleanOldFiles(cacheDir, now, maxAge)
            calculateCacheSize()
        }
    }

    private fun cleanOldFiles(dir: File, now: Long, maxAge: Long) {
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    cleanOldFiles(file, now, maxAge)
                } else {
                    if (now - file.lastModified() > maxAge) {
                        file.delete()
                    }
                }
            }
        }
    }

    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _typingIndicatorsEnabled.value = enabled
            val userId = stateManager.loadCurrentUserId()
            if (userId != null && userId > 0) {
                userRepository.getUserById(userId).onSuccess { user ->
                    userRepository.updateProfile(
                        displayName = user.displayName ?: "",
                        bio = user.bio ?: "",
                        avatarUrl = user.avatarUrl,
                        email = user.email,
                        password = null,
                        readReceiptsEnabled = null,
                        typingIndicatorsEnabled = enabled
                    )
                }
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _isLoadingSessions.value = true
            try {
                val response = apiService.getSessions()
                if (response.isSuccessful) {
                    _sessions.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to load sessions", e)
            } finally {
                _isLoadingSessions.value = false
            }
        }
    }

    fun logoutSession(sessionId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteSession(sessionId)
                if (response.isSuccessful) {
                    _sessions.value = _sessions.value.filter { it.id != sessionId }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to logout session", e)
            }
        }
    }

    fun logoutAllOtherSessions() {
        viewModelScope.launch {
            try {
                val response = apiService.deleteAllOtherSessions()
                if (response.isSuccessful) {
                    _sessions.value = _sessions.value.filter { it.isCurrent }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to logout other sessions", e)
            }
        }
    }
}
