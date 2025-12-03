package com.nexy.client.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.datastore.preferences.core.stringPreferencesKey

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val THEME_STYLE_KEY = stringPreferencesKey("theme_style")
    private val FONT_SCALE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("font_scale")
    private val INCOMING_TEXT_COLOR_KEY = androidx.datastore.preferences.core.longPreferencesKey("incoming_text_color")
    private val OUTGOING_TEXT_COLOR_KEY = androidx.datastore.preferences.core.longPreferencesKey("outgoing_text_color")
    
    private val _isDarkTheme = MutableStateFlow(true) // Default to Dark
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    
    private val _themeStyle = MutableStateFlow(ThemeStyle.Pink)
    val themeStyle: StateFlow<ThemeStyle> = _themeStyle.asStateFlow()

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _incomingTextColor = MutableStateFlow(0xFF000000) // Default Black (will be adjusted based on theme usually, but here we store specific override)
    val incomingTextColor: StateFlow<Long> = _incomingTextColor.asStateFlow()

    private val _outgoingTextColor = MutableStateFlow(0xFF000000) // Default Black
    val outgoingTextColor: StateFlow<Long> = _outgoingTextColor.asStateFlow()
    
    init {
        loadThemePreference()
    }
    
    private fun loadThemePreference() {
        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                _isDarkTheme.value = preferences[DARK_THEME_KEY] ?: true
                val styleName = preferences[THEME_STYLE_KEY] ?: ThemeStyle.Pink.name
                _themeStyle.value = try {
                    ThemeStyle.valueOf(styleName)
                } catch (e: IllegalArgumentException) {
                    ThemeStyle.Pink
                }
                _fontScale.value = preferences[FONT_SCALE_KEY] ?: 1.0f
                
                // Default colors if not set. 
                // For dark theme, default text is usually white/light. For light theme, black/dark.
                // We'll use 0 to indicate "use default theme color" or store actual ARGB values.
                // Let's store actual ARGB. Default to 0 (unset) and handle in UI? 
                // Or set defaults here. Let's use 0 as "Not Set" and handle defaults in UI.
                _incomingTextColor.value = preferences[INCOMING_TEXT_COLOR_KEY] ?: 0L
                _outgoingTextColor.value = preferences[OUTGOING_TEXT_COLOR_KEY] ?: 0L
            }
        }
    }
    
    fun toggleTheme() {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                val current = preferences[DARK_THEME_KEY] ?: true
                preferences[DARK_THEME_KEY] = !current
            }
        }
    }
    
    fun setThemeStyle(style: ThemeStyle) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[THEME_STYLE_KEY] = style.name
            }
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            _fontScale.value = scale
            context.dataStore.edit { preferences ->
                preferences[FONT_SCALE_KEY] = scale
            }
        }
    }

    fun setIncomingTextColor(color: Long) {
        viewModelScope.launch {
            _incomingTextColor.value = color
            context.dataStore.edit { preferences ->
                preferences[INCOMING_TEXT_COLOR_KEY] = color
            }
        }
    }

    fun setOutgoingTextColor(color: Long) {
        viewModelScope.launch {
            _outgoingTextColor.value = color
            context.dataStore.edit { preferences ->
                preferences[OUTGOING_TEXT_COLOR_KEY] = color
            }
        }
    }
}
