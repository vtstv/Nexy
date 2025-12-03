package com.nexy.client.e2e

import android.content.Context
import android.util.Base64
import android.util.Log
import com.nexy.client.data.local.AuthTokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E2E Manager - coordinator for all E2E encryption operations
 */
@Singleton
class E2EManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authTokenManager: AuthTokenManager,
    private val e2eApiClient: E2EApiClient
) {
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private var cryptoManager: E2ECryptoManager? = null
    private var sessionManager: E2ESessionManager? = null
    
    companion object {
        private const val TAG = "E2EManager"
    }
    
    /**
     * Initialize E2E for current user
     */
    suspend fun initialize() {
        val userId = authTokenManager.getUserId() ?: run {
            Log.w(TAG, "Cannot initialize E2E: no user ID")
            return
        }
        
        Log.d(TAG, "Initializing E2E for user $userId")
        
        cryptoManager = E2ECryptoManager(context, userId)
        sessionManager = E2ESessionManager(cryptoManager!!)
        
        val isFirstTime = cryptoManager!!.initializeKeys()
        
        if (isFirstTime) {
            Log.d(TAG, "First time E2E setup - uploading keys")
            uploadKeysToServer()
        } else {
            Log.d(TAG, "E2E keys already exist - checking pre-key count")
            checkAndReplenishPreKeys()
        }
        
        _isInitialized.value = true
    }
    
    /**
     * Upload keys to server
     */
    private suspend fun uploadKeysToServer() {
        val crypto = cryptoManager ?: return
        val token = authTokenManager.getAccessToken() ?: return
        
        try {
            val identityKey = crypto.getIdentityPublicKey()
            val signedPreKeyPublic = crypto.generatePreKey(1)
            
            // Generate 100 pre-keys
            val preKeysList = (1..100).map { keyId ->
                PreKeyData(
                    keyId = keyId,
                    publicKey = crypto.generatePreKey(keyId)
                )
            }
            
            val request = KeyUploadRequest(
                identityKey = identityKey,
                signedPreKey = SignedPreKeyData(
                    keyId = 1,
                    publicKey = signedPreKeyPublic,
                    signature = "simplified" // Simplified version without signature
                ),
                preKeys = preKeysList
            )
            
            val result = e2eApiClient.uploadKeys(token, request)
            result.onSuccess {
                Log.d(TAG, "Keys uploaded successfully")
            }.onFailure { error ->
                Log.e(TAG, "Failed to upload keys", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading keys", e)
        }
    }
    
    /**
     * Check and replenish one-time pre-keys if needed
     */
    private suspend fun checkAndReplenishPreKeys() {
        val crypto = cryptoManager ?: return
        val token = authTokenManager.getAccessToken() ?: return
        
        try {
            val result = e2eApiClient.checkPreKeyCount(token)
            result.onSuccess { response ->
                Log.d(TAG, "Current pre-key count: ${response.count}")
                
                if (response.needsMore) {
                    Log.d(TAG, "Replenishing pre-keys...")
                    // Generate 50 new pre-keys
                    val startId = response.count + 1
                    (startId until startId + 50).forEach { keyId ->
                        crypto.generatePreKey(keyId)
                    }
                    Log.d(TAG, "Generated 50 new pre-keys")
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to check pre-key count", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pre-key count", e)
        }
    }
    
    /**
     * Encrypt message
     */
    fun encryptMessage(recipientUserId: Int, plaintext: String): EncryptedMessage? {
        val session = sessionManager ?: run {
            Log.w(TAG, "E2E not initialized")
            return null
        }
        
        return try {
            session.encryptMessage(recipientUserId, plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt message", e)
            null
        }
    }
    
    /**
     * Decrypt message
     */
    fun decryptMessage(encrypted: EncryptedMessage, senderUserId: Int): String? {
        val session = sessionManager ?: run {
            Log.w(TAG, "E2E not initialized")
            return null
        }
        
        return try {
            session.decryptMessage(encrypted, senderUserId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt message", e)
            null
        }
    }
    
    /**
     * Check if E2E is initialized
     */
    fun isE2EReady(): Boolean {
        return cryptoManager?.isInitialized() == true
    }
}
