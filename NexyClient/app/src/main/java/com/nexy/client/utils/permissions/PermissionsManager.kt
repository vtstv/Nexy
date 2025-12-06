/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
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
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun isNotificationPermissionGranted(activity: AppCompatActivity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
    }
    
    /**
     * Request notification permission with explanation dialog
     */
    fun requestNotificationPermission(activity: AppCompatActivity, showRationale: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return // Not required for older versions
        }
        
        if (isNotificationPermissionGranted(activity)) {
            return // Already granted
        }
        
        if (showRationale && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )) {
            // Show explanation dialog
            AlertDialog.Builder(activity)
                .setTitle("Enable Notifications")
                .setMessage("Nexy needs notification permission to alert you about new messages when the app is in the background.\n\nWithout this permission, you won't receive message notifications.")
                .setPositiveButton("Allow") { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_CODE_NOTIFICATIONS_AND_AUDIO
                    )
                }
                .setNegativeButton("Not Now", null)
                .show()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATIONS_AND_AUDIO
            )
        }
    }
    
    /**
     * Open app settings for manual permission grant
     */
    fun openAppSettings(activity: AppCompatActivity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
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
        
        // Check notification permission
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Check audio permission
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissions.isNotEmpty()) {
            // Show rationale if needed
            val shouldShowRationale = permissions.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
            
            if (shouldShowRationale && permissions.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                // Show explanation dialog for notifications
                AlertDialog.Builder(activity)
                    .setTitle("Permissions Required")
                    .setMessage("Nexy needs these permissions:\n\n" +
                            "• Notifications: To alert you about new messages\n" +
                            "• Microphone: For voice and video calls\n\n" +
                            "Without these permissions, some features won't work.")
                    .setPositiveButton("Allow") { _, _ ->
                        ActivityCompat.requestPermissions(
                            activity,
                            permissions.toTypedArray(),
                            REQUEST_CODE_NOTIFICATIONS_AND_AUDIO
                        )
                    }
                    .setNegativeButton("Not Now") { _, _ ->
                        webRTCClient.initialize() // Initialize anyway
                    }
                    .show()
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    permissions.toTypedArray(),
                    REQUEST_CODE_NOTIFICATIONS_AND_AUDIO
                )
            }
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
