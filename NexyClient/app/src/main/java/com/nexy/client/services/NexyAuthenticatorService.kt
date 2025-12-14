package com.nexy.client.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NexyAuthenticatorService : Service() {
    
    private lateinit var authenticator: NexyAccountAuthenticator
    
    override fun onCreate() {
        super.onCreate()
        authenticator = NexyAccountAuthenticator(this)
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return authenticator.iBinder
    }
}
