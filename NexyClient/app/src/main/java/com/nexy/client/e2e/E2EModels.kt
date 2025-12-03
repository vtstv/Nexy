package com.nexy.client.e2e

import com.google.gson.annotations.SerializedName

/**
 * Request to upload keys to server
 */
data class KeyUploadRequest(
    @SerializedName("identity_key")
    val identityKey: String,
    @SerializedName("signed_pre_key")
    val signedPreKey: SignedPreKeyData,
    @SerializedName("pre_keys")
    val preKeys: List<PreKeyData>
)

/**
 * Signed PreKey data
 */
data class SignedPreKeyData(
    @SerializedName("key_id")
    val keyId: Int,
    @SerializedName("public_key")
    val publicKey: String,
    @SerializedName("signature")
    val signature: String
)

/**
 * One-time PreKey data
 */
data class PreKeyData(
    @SerializedName("key_id")
    val keyId: Int,
    @SerializedName("public_key")
    val publicKey: String
)

/**
 * User's Key Bundle from server
 */
data class KeyBundle(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("identity_key")
    val identityKey: String,
    @SerializedName("signed_pre_key")
    val signedPreKey: String,
    @SerializedName("signed_pre_key_id")
    val signedPreKeyId: Int,
    @SerializedName("signature")
    val signature: String,
    @SerializedName("pre_key")
    val preKey: String? = null,
    @SerializedName("pre_key_id")
    val preKeyId: Int? = null,
    @SerializedName("device_id")
    val deviceId: Int
)

/**
 * Response with keys
 */
data class KeyBundleResponse(
    @SerializedName("bundle")
    val bundle: KeyBundle
)

/**
 * Number of available PreKeys
 */
data class PreKeyCountResponse(
    @SerializedName("count")
    val count: Int,
    @SerializedName("needs_more")
    val needsMore: Boolean
)

/**
 * Encrypted message
 */
data class EncryptedMessage(
    val ciphertext: String,
    val iv: String,
    val algorithm: String
)

/**
 * Encryption information in message body
 */
data class Encryption(
    val algorithm: String,
    @SerializedName("key_id")
    val keyId: String? = null
)
