package com.nexy.client.e2e

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.KeyGenerator

/**
 * E2E Crypto Manager - manages encryption keys
 * Uses Java Crypto API for key generation and storage
 */
class E2ECryptoManager(private val context: Context, private val userId: Int) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "e2e_keys_$userId",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val IDENTITY_KEY_PAIR = "identity_key_pair"
        private const val REGISTRATION_ID = "registration_id"
        private const val SIGNED_PRE_KEY_ID = "signed_pre_key_id"
        private const val PRE_KEY_ID_OFFSET = "pre_key_id_offset"
    }
    
    /**
     * Initialize keys on first launch
     * @return true if keys created for first time, false if already exist
     */
    fun initializeKeys(): Boolean {
        if (prefs.contains(IDENTITY_KEY_PAIR)) {
            return false // Already initialized
        }
        
        // Generate RSA key pair for identity
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()
        
        // Save public key as identity
        val publicKeyBytes = keyPair.public.encoded
        val serialized = Base64.encodeToString(publicKeyBytes, Base64.DEFAULT)
        prefs.edit().putString(IDENTITY_KEY_PAIR, serialized).apply()
        
        // Generate registration ID
        val registrationId = SecureRandom().nextInt(16384)
        prefs.edit().putInt(REGISTRATION_ID, registrationId).apply()
        
        // Initialize counters
        prefs.edit().putInt(SIGNED_PRE_KEY_ID, 1).apply()
        prefs.edit().putInt(PRE_KEY_ID_OFFSET, 1).apply()
        
        return true
    }
    
    /**
     * Get identity public key
     */
    fun getIdentityPublicKey(): String {
        return prefs.getString(IDENTITY_KEY_PAIR, null)
            ?: throw IllegalStateException("Identity key not found. Call initializeKeys() first.")
    }
    
    /**
     * Generate new PreKey
     * Simplified version without signature
     */
    fun generatePreKey(keyId: Int): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        val key = keyGen.generateKey()
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }
    
    /**
     * Get Registration ID
     */
    fun getRegistrationId(): Int {
        return prefs.getInt(REGISTRATION_ID, 0)
    }
    
    /**
     * Check if keys are initialized
     */
    fun isInitialized(): Boolean {
        return prefs.contains(IDENTITY_KEY_PAIR)
    }
}
