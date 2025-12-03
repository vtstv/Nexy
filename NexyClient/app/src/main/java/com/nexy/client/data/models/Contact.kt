package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

/**
 * Contact model matching server's Contact and ContactWithUser structures
 */
data class Contact(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("contact_user_id")
    val contactUserId: Int,
    
    @SerializedName("status")
    val status: ContactStatus,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

enum class ContactStatus {
    @SerializedName("accepted")
    ACCEPTED,
    
    @SerializedName("blocked")
    BLOCKED
}

/**
 * Contact with full user information
 */
data class ContactWithUser(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("contact_user_id")
    val contactUserId: Int,
    
    @SerializedName("status")
    val status: ContactStatus,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    
    @SerializedName("contact_user")
    val contactUser: User
)

/**
 * Request to add a contact
 */
data class AddContactRequest(
    @SerializedName("contact_user_id")
    val contactUserId: Int
)

/**
 * Request to update contact status
 */
data class UpdateContactStatusRequest(
    @SerializedName("status")
    val status: ContactStatus
)

/**
 * Response for checking contact status
 */
data class ContactStatusResponse(
    @SerializedName("exists")
    val exists: Boolean,
    
    @SerializedName("status")
    val status: String?
)
