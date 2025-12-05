package com.nexy.client

import android.app.Application
import android.util.Log
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.network.NetworkMonitor
import com.nexy.client.data.sync.MessageQueueManager
import com.nexy.client.data.sync.SyncManager
import com.nexy.client.e2e.E2EManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NexyApplication : Application() {
    
    companion object {
        private const val TAG = "NexyApplication"
    }
    
    @Inject
    lateinit var e2eManager: E2EManager
    
    @Inject
    lateinit var authTokenManager: AuthTokenManager
    
    @Inject
    lateinit var networkMonitor: NetworkMonitor
    
    @Inject
    lateinit var messageQueueManager: MessageQueueManager
    
    @Inject
    lateinit var syncManager: SyncManager
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        networkMonitor.startMonitoring()
        Log.d(TAG, "Network monitoring started")
        
        applicationScope.launch {
            if (authTokenManager.isLoggedIn()) {
                messageQueueManager.start()
                Log.d(TAG, "Message queue manager started")
                
                Log.d(TAG, "Checking for unsent messages on startup")
                messageQueueManager.checkAndFlushPendingMessages()
                
                if (syncManager.isSyncNeeded()) {
                    Log.d(TAG, "Sync needed, fetching difference")
                    syncManager.syncDifference()
                }
                
                if (!BuildConfig.DEBUG) {
                    Log.d(TAG, "Production build - initializing E2E encryption")
                    e2eManager.initialize()
                }
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        networkMonitor.stopMonitoring()
        messageQueueManager.stop()
    }
}

