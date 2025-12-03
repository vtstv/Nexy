/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor() {
    // Default to locked so app starts locked if PIN is enabled
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastActiveTime: Long = System.currentTimeMillis()
    private var isPinEnabled: Boolean = false
    
    // Lock timeout in milliseconds (e.g., 1 minute)
    // TODO: Make this configurable via settings
    private val LOCK_TIMEOUT = 60 * 1000L 

    fun setPinEnabled(enabled: Boolean) {
        isPinEnabled = enabled
    }

    fun onAppForeground() {
        if (isPinEnabled) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastActiveTime > LOCK_TIMEOUT) {
                _isLocked.value = true
            }
        }
        lastActiveTime = System.currentTimeMillis()
    }

    fun onAppBackground() {
        lastActiveTime = System.currentTimeMillis()
    }

    fun unlock() {
        _isLocked.value = false
        lastActiveTime = System.currentTimeMillis()
    }
    
    fun lockImmediately() {
        if (isPinEnabled) {
            _isLocked.value = true
        }
    }
}
