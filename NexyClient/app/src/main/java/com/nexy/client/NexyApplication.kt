package com.nexy.client

import android.app.Application
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.e2e.E2EManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NexyApplication : Application() {
    
    @Inject
    lateinit var e2eManager: E2EManager
    
    @Inject
    lateinit var authTokenManager: AuthTokenManager
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize E2E encryption in production builds when user is logged in
        if (!BuildConfig.DEBUG) {
            applicationScope.launch {
                if (authTokenManager.isLoggedIn()) {
                    android.util.Log.d("NexyApplication", "Production build - initializing E2E encryption")
                    e2eManager.initialize()
                }
            }
        }
    }
}

