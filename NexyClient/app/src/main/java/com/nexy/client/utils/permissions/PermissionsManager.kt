/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nexy.client.data.webrtc.WebRTCClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsManager @Inject constructor(
    private val webRTCClient: WebRTCClient
) {
    companion object {
        const val REQUEST_CODE_NOTIFICATIONS_AND_AUDIO = 101
        const val REQUEST_CODE_AUDIO_ONLY = 102
    }

    fun requestRequiredPermissions(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionsForAndroid13Plus(activity)
        } else {
            requestPermissionsForOlderVersions(activity)
        }
    }

    private fun requestPermissionsForAndroid13Plus(activity: AppCompatActivity) {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissions.toTypedArray(),
                REQUEST_CODE_NOTIFICATIONS_AND_AUDIO
            )
        } else {
            webRTCClient.initialize()
        }
    }

    private fun requestPermissionsForOlderVersions(activity: AppCompatActivity) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_AUDIO_ONLY
            )
        } else {
            webRTCClient.initialize()
        }
    }

    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_NOTIFICATIONS_AND_AUDIO || 
            requestCode == REQUEST_CODE_AUDIO_ONLY) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                webRTCClient.initialize()
            }
        }
    }
}
