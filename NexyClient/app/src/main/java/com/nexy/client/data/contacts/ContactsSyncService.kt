package com.nexy.client.data.contacts

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.nexy.client.data.models.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsSyncService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ContactsSyncService"
        private const val NEXY_ACCOUNT_TYPE = "com.nexy.client"
        private const val NEXY_ACCOUNT_NAME = "Nexy"
        private const val NEXY_MIME_TYPE = "vnd.android.cursor.item/com.nexy.client.profile"
    }

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
                val normalizedNumber = normalizePhoneNumber(number)
                if (normalizedNumber.isNotEmpty()) {
                    phoneNumbers.add(normalizedNumber)
                }
            }
        }
        
        phoneNumbers.toList()
    }

    private fun normalizePhoneNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        
        val cleaned = number.replace(Regex("[^0-9+]"), "")
        
        if (cleaned.startsWith("8") && cleaned.length == 11) {
            return "+7" + cleaned.substring(1)
        }
        
        return if (cleaned.startsWith("+")) cleaned else "+$cleaned"
    }

    suspend fun addNexyIndicatorToContact(user: User): Boolean = withContext(Dispatchers.IO) {
        if (user.phoneNumber.isNullOrEmpty()) {
            Log.d(TAG, "User ${user.username} has no phone number")
            return@withContext false
        }
        
        try {
            // Find the existing contact
            val contactInfo = findContactByPhone(user.phoneNumber)
            if (contactInfo == null) {
                Log.d(TAG, "No contact found for phone ${user.phoneNumber}")
                return@withContext false
            }
            
            val (contactId, existingRawContactId) = contactInfo
            Log.d(TAG, "Found contact $contactId (rawContact $existingRawContactId) for user ${user.username}")
            
            // Check if we already have a Nexy raw contact for this contact
            val existingNexyRawContact = findNexyRawContactForContact(contactId)
            if (existingNexyRawContact != null) {
                Log.d(TAG, "Nexy entry already exists for contact $contactId (nexyRawContact $existingNexyRawContact)")
                return@withContext true
            }
            
            // Create a new Nexy raw contact with aggregation
            val nexyAccount = Account(NEXY_ACCOUNT_NAME, NEXY_ACCOUNT_TYPE)
            val operations = ArrayList<ContentProviderOperation>()
            
            // 1. Create new raw contact for Nexy
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, NEXY_ACCOUNT_TYPE)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, NEXY_ACCOUNT_NAME)
                    .build()
            )
            
            // 2. Add Nexy profile data
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, NEXY_MIME_TYPE)
                    .withValue(ContactsContract.Data.DATA1, user.phoneNumber)
                    .withValue(ContactsContract.Data.DATA2, "Nexy")
                    .withValue(ContactsContract.Data.DATA3, "Open in Nexy")
                    .withValue(ContactsContract.Data.DATA4, user.id.toString())
                    .withValue(ContactsContract.Data.DATA5, user.username)
                    .build()
            )
            
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val newNexyRawContactId = results[0].uri?.lastPathSegment?.toLongOrNull()
            Log.d(TAG, "Created Nexy raw contact $newNexyRawContactId for user ${user.username}")
            
            // 4. Aggregate the new Nexy raw contact with the existing contact
            if (newNexyRawContactId != null) {
                aggregateContacts(existingRawContactId, newNexyRawContactId)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding Nexy indicator for ${user.username}", e)
            false
        }
    }
    
    private fun findNexyRawContactForContact(contactId: Long): Long? {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(contactId.toString(), NEXY_ACCOUNT_TYPE),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else null
        }
    }
    
    private fun aggregateContacts(rawContactId1: Long, rawContactId2: Long) {
        try {
            val operations = ArrayList<ContentProviderOperation>()
            operations.add(
                ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawContactId1)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, rawContactId2)
                    .build()
            )
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            Log.d(TAG, "Aggregated raw contacts $rawContactId1 and $rawContactId2")
        } catch (e: Exception) {
            Log.e(TAG, "Error aggregating contacts", e)
        }
    }

    suspend fun removeAllNexyIndicators(): Int = withContext(Dispatchers.IO) {
        try {
            // Delete all Nexy raw contacts (which will also delete their Data entries)
            val deletedRawContacts = context.contentResolver.delete(
                ContactsContract.RawContacts.CONTENT_URI,
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
                arrayOf(NEXY_ACCOUNT_TYPE)
            )
            
            // Also clean up any orphaned Nexy Data entries
            val deletedData = context.contentResolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(NEXY_MIME_TYPE)
            )
            
            val total = deletedRawContacts + deletedData
            Log.d(TAG, "Removed Nexy data: $deletedRawContacts raw contacts, $deletedData data entries")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Nexy indicators", e)
            0
        }
    }

    suspend fun syncNexyUsersWithContacts(nexyUsers: List<User>): Int = withContext(Dispatchers.IO) {
        var syncedCount = 0
        nexyUsers.forEach { user ->
            if (addNexyIndicatorToContact(user)) {
                syncedCount++
            }
        }
        Log.d(TAG, "Synced $syncedCount of ${nexyUsers.size} users")
        syncedCount
    }

    private fun findContactByPhone(phoneNumber: String): Pair<Long, Long>? {
        val normalizedPhone = normalizePhoneNumber(phoneNumber)
        
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            ),
            // Exclude Nexy account contacts
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR ${ContactsContract.RawContacts.ACCOUNT_TYPE} != ?",
            arrayOf(NEXY_ACCOUNT_TYPE),
            null
        )
        
        cursor?.use {
            val contactIdIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val rawContactIdIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (it.moveToNext()) {
                val number = it.getString(numberIndex) ?: ""
                val contactNormalized = normalizePhoneNumber(number)
                
                // Match by normalized phone or by last 10 digits
                if (contactNormalized == normalizedPhone ||
                    contactNormalized.takeLast(10) == normalizedPhone.takeLast(10)) {
                    val contactId = it.getLong(contactIdIndex)
                    val rawContactId = it.getLong(rawContactIdIndex)
                    return Pair(contactId, rawContactId)
                }
            }
        }
        return null
    }
}
