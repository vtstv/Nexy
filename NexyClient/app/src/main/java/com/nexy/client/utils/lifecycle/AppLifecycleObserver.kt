/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nexy.client.utils.PinManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLifecycleObserver @Inject constructor(
    private val pinManager: PinManager
) : DefaultLifecycleObserver {
    
    override fun onStart(owner: LifecycleOwner) {
        pinManager.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        pinManager.onAppBackground()
    }
}
