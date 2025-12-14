package com.nexy.client.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NexySyncService : Service() {
    
    private lateinit var syncAdapter: NexySyncAdapter
    private val syncAdapterLock = Any()
    
    override fun onCreate() {
        super.onCreate()
        synchronized(syncAdapterLock) {
            syncAdapter = NexySyncAdapter(applicationContext, true)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return syncAdapter.syncAdapterBinder
    }
}
