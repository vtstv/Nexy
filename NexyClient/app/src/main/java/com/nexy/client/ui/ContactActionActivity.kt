package com.nexy.client.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.ComponentActivity

class ContactActionActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "ContactActionActivity"
        private const val MIME_TYPE_PROFILE = "vnd.android.cursor.item/com.nexy.client.profile"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
        
        val data = intent.data
        if (data == null) {
            finish()
            return
        }
        
        Log.d(TAG, "Received intent with data: $data")
        
        try {
            // Extract user ID from the contact data
            val userId = extractNexyUserId(data)
            
            Log.d(TAG, "Extracted userId: $userId")
            
            if (userId != null) {
                // Launch main app and navigate to chat
                val mainIntent = Intent(this, com.nexy.client.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_chat_with_user_id", userId)
                }
                startActivity(mainIntent)
            } else {
                // Just open the app
                val mainIntent = Intent(this, com.nexy.client.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(mainIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling contact action", e)
        }
        
        finish()
    }
    
    private fun extractNexyUserId(data: Uri): Int? {
        return try {
            contentResolver.query(
                data,
                arrayOf(ContactsContract.Data.DATA4),  // user ID stored in DATA4
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.toIntOrNull()
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting Nexy user ID", e)
            null
        }
    }
}
