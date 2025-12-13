package com.nexy.client.data.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.nexy.client.data.models.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for syncing Nexy contacts with the device's contacts/dialer app.
 * This allows users to see which contacts use Nexy directly in their phone's dialer.
 */
@Singleton
class ContactsSyncService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val NEXY_ACCOUNT_TYPE = "com.nexy.client"
        private const val NEXY_ACCOUNT_NAME = "Nexy"
        private const val NEXY_MIME_TYPE = "vnd.android.cursor.item/com.nexy.client.profile"
    }

    /**
     * Get all phone numbers from device contacts
     */
    suspend fun getDeviceContactPhoneNumbers(): List<String> = withContext(Dispatchers.IO) {
        val phoneNumbers = mutableSetOf<String>()
        
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )
        
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val number = it.getString(numberIndex)
                // Normalize phone number (remove spaces, dashes, etc.)
                val normalizedNumber = normalizePhoneNumber(number)
                if (normalizedNumber.isNotEmpty()) {
                    phoneNumbers.add(normalizedNumber)
                }
            }
        }
        
        phoneNumbers.toList()
    }

    /**
     * Normalize phone number to international format
     */
    private fun normalizePhoneNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        
        // Remove all non-digit characters except leading +
        val cleaned = number.replace(Regex("[^0-9+]"), "")
        
        // If starts with 8, replace with +7 (Russian format)
        if (cleaned.startsWith("8") && cleaned.length == 11) {
            return "+7" + cleaned.substring(1)
        }
        
        // If doesn't start with +, assume it might need country code
        return if (cleaned.startsWith("+")) cleaned else cleaned
    }

    /**
     * Add Nexy indicator to a contact in the device contacts.
     * This will show "Nexy" as a communication option in the dialer.
     */
    suspend fun addNexyIndicatorToContact(user: User): Boolean = withContext(Dispatchers.IO) {
        if (user.phoneNumber.isNullOrEmpty()) return@withContext false
        
        try {
            // Find contact by phone number
            val contactId = findContactByPhone(user.phoneNumber) ?: return@withContext false
            val rawContactId = getRawContactId(contactId) ?: return@withContext false
            
            // Check if Nexy entry already exists
            if (hasNexyEntry(rawContactId)) return@withContext true
            
            // Add Nexy entry to contact
            val operations = ArrayList<ContentProviderOperation>()
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, NEXY_MIME_TYPE)
                    .withValue(ContactsContract.Data.DATA1, user.username)
                    .withValue(ContactsContract.Data.DATA2, "Nexy")
                    .withValue(ContactsContract.Data.DATA3, user.id.toString())
                    .build()
            )
            
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Remove Nexy indicator from all contacts
     */
    suspend fun removeAllNexyIndicators(): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(NEXY_MIME_TYPE)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Sync Nexy users with device contacts - add Nexy indicator to matching contacts
     */
    suspend fun syncNexyUsersWithContacts(nexyUsers: List<User>): Int = withContext(Dispatchers.IO) {
        var syncedCount = 0
        nexyUsers.forEach { user ->
            if (addNexyIndicatorToContact(user)) {
                syncedCount++
            }
        }
        syncedCount
    }

    private fun findContactByPhone(phoneNumber: String): Long? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} = ?",
            arrayOf(phoneNumber, phoneNumber),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
            }
        }
        return null
    }

    private fun getRawContactId(contactId: Long): Long? {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
            }
        }
        return null
    }

    private fun hasNexyEntry(rawContactId: Long): Boolean {
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), NEXY_MIME_TYPE),
            null
        )
        
        cursor?.use {
            return it.count > 0
        }
        return false
    }
}
