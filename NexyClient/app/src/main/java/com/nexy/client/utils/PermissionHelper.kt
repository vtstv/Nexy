package com.nexy.client.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    val CAMERA_PERMISSION = Manifest.permission.CAMERA
    val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    val READ_STORAGE_PERMISSION = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            AUDIO_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            READ_STORAGE_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasAllCallPermissions(context: Context): Boolean {
        return hasCameraPermission(context) && hasAudioPermission(context)
    }
}
