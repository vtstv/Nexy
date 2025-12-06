package com.nexy.client.data.fcm

import android.util.Log
import com.nexy.client.BuildConfig
import com.nexy.client.data.api.NexyApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmManager @Inject constructor(
    private val apiService: NexyApiService
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "FcmManager"
        
        fun isFcmEnabled(): Boolean = BuildConfig.FCM_ENABLED
    }
    
    /**
     * Initialize FCM if enabled in BuildConfig
     */
    fun initializeFcm() {
        if (!isFcmEnabled()) {
            Log.d(TAG, "FCM is disabled in BuildConfig")
            return
        }
        
        try {
            // Initialize Firebase Messaging - this will be done via reflection
            // to avoid compile errors when google-services.json is missing
            val firebaseClass = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            val getInstance = firebaseClass.getMethod("getInstance")
            val firebaseMessaging = getInstance.invoke(null)
            
            val getTokenMethod = firebaseClass.getMethod("getToken")
            val taskClass = Class.forName("com.google.android.gms.tasks.Task")
            val task = getTokenMethod.invoke(firebaseMessaging)
            
            // Add success listener
            val addOnSuccessListenerMethod = taskClass.getMethod(
                "addOnSuccessListener",
                Class.forName("com.google.android.gms.tasks.OnSuccessListener")
            )
            
            addOnSuccessListenerMethod.invoke(task, object : com.google.android.gms.tasks.OnSuccessListener<Any> {
                override fun onSuccess(result: Any?) {
                    val token = result as? String
                    if (token != null) {
                        Log.d(TAG, "FCM Token retrieved successfully")
                        sendTokenToServer(token)
                    }
                }
            })
            
            // Add failure listener
            val addOnFailureListenerMethod = taskClass.getMethod(
                "addOnFailureListener",
                Class.forName("com.google.android.gms.tasks.OnFailureListener")
            )
            
            addOnFailureListenerMethod.invoke(task, object : com.google.android.gms.tasks.OnFailureListener {
                override fun onFailure(e: Exception) {
                    Log.e(TAG, "Failed to get FCM token", e)
                }
            })
            
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Firebase classes not found - FCM disabled", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM", e)
        }
    }
    
    /**
     * Send FCM token to server
     */
    fun sendTokenToServer(token: String) {
        if (!isFcmEnabled()) {
            Log.d(TAG, "FCM disabled - not sending token to server")
            return
        }
        
        scope.launch {
            try {
                val response = apiService.updateFcmToken(mapOf("fcm_token" to token))
                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token sent to server successfully")
                } else {
                    Log.e(TAG, "Failed to send FCM token: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending FCM token to server", e)
            }
        }
    }
    
    /**
     * Delete FCM token from server
     */
    fun deleteTokenFromServer() {
        if (!isFcmEnabled()) {
            return
        }
        
        scope.launch {
            try {
                val response = apiService.deleteFcmToken()
                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token deleted from server")
                } else {
                    Log.e(TAG, "Failed to delete FCM token: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting FCM token from server", e)
            }
        }
    }
}
