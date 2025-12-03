package com.nexy.client.e2e

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Session Manager for message encryption/decryption
 * Uses AES-256-GCM for content encryption
 */
class E2ESessionManager(private val cryptoManager: E2ECryptoManager) {
    
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 32 // 256 bits
        private const val IV_SIZE = 12 // 96 bits (recommended for GCM)
        private const val TAG_SIZE = 128 // 128 bits authentication tag
    }
    
    /**
     * Encrypt message for recipient
     * @param recipientUserId recipient ID
     * @param plaintext message text
     * @return encrypted message with IV
     */
    fun encryptMessage(recipientUserId: Int, plaintext: String): EncryptedMessage {
        val secretKey = generateSessionKey(recipientUserId)
        val cipher = Cipher.getInstance(ALGORITHM)
        
        // Generate random IV
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        val gcmSpec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        return EncryptedMessage(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            algorithm = "AES-256-GCM"
        )
    }
    
    /**
     * Decrypt message from sender
     * @param encrypted encrypted message
     * @param senderUserId sender ID
     * @return decrypted text
     */
    fun decryptMessage(encrypted: EncryptedMessage, senderUserId: Int): String {
        val secretKey = generateSessionKey(senderUserId)
        val cipher = Cipher.getInstance(ALGORITHM)
        
        val iv = Base64.decode(encrypted.iv, Base64.NO_WRAP)
        val gcmSpec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        val ciphertext = Base64.decode(encrypted.ciphertext, Base64.NO_WRAP)
        val plaintext = cipher.doFinal(ciphertext)
        
        return String(plaintext, Charsets.UTF_8)
    }
    
    /**
     * Generate session key for user
     * Simplified version: use derivative from identity key
     * In production: use Signal Protocol's Double Ratchet
     */
    private fun generateSessionKey(userId: Int): SecretKeySpec {
        val identityKey = cryptoManager.getIdentityPublicKey()
        val keyMaterial = Base64.decode(identityKey, Base64.DEFAULT) + userId.toString().toByteArray()
        
        // SHA-256 hash to get 256-bit key
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(keyMaterial)
        
        return SecretKeySpec(hash, "AES")
    }
    
    /**
     * Check if message can be decrypted
     */
    fun canDecrypt(encrypted: EncryptedMessage): Boolean {
        return try {
            Base64.decode(encrypted.ciphertext, Base64.NO_WRAP)
            Base64.decode(encrypted.iv, Base64.NO_WRAP)
            true
        } catch (e: Exception) {
            false
        }
    }
}
