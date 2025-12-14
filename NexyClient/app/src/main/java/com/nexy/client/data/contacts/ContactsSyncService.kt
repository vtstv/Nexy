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
        private const val NEXY_MIME_TYPE = ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
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

    @androidx.compose.runtime.NoLiveLiterals
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
            
            // Check if we already have Nexy data for this raw contact
            if (hasNexyData(existingRawContactId)) {
                Log.d(TAG, "Nexy data already exists for raw contact $existingRawContactId")
                return@withContext true
            }
            
            // Add Nexy data to the existing raw contact
            val operations = ArrayList<ContentProviderOperation>()
            
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, existingRawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, NEXY_MIME_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Im.DATA, user.id.toString())
                    .withValue(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM)
                    .withValue(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, "Nexy")
                    .withValue(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_WORK)
                    .withValue(ContactsContract.CommonDataKinds.Im.LABEL, user.username)
                    .build()
            )
            
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            Log.d(TAG, "Added Nexy data to existing raw contact $existingRawContactId for user ${user.username}")
            
            // Notify contacts changed
            context.contentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null)
            
            true
            
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
    
    private fun hasNexyData(rawContactId: Long): Boolean {
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.DATA6} = ?",
            arrayOf(rawContactId.toString(), NEXY_MIME_TYPE, "Nexy"),
            null
        )
        return cursor?.use { it.count > 0 } ?: false
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
            val deletedDataIm = context.contentResolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
            )
            
            val deletedDataCustom = context.contentResolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf("vnd.android.cursor.item/vnd.com.nexy.profile")
            )
            
            val total = deletedRawContacts + deletedDataIm + deletedDataCustom
            Log.d(TAG, "Removed Nexy data: $deletedRawContacts raw contacts, $deletedDataIm IM data entries, $deletedDataCustom custom data entries")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Nexy indicators", e)
            0
        }
    }

    suspend fun addOrUpdateNexyContact(user: User): Boolean = withContext(Dispatchers.IO) {
        val phoneToUse = user.phoneNumber ?: "nexy${user.id}"
        
        val existingContact = findContactByPhone(phoneToUse)
        if (existingContact != null) {
            // Add indicator to existing contact
            addNexyIndicatorToContact(user)
        } else {
            // Create new full contact
            createNewNexyContact(user, phoneToUse)
        }
    }

    private suspend fun createNewNexyContact(user: User, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val operations = ArrayList<ContentProviderOperation>()
            val nexyAccount = Account(NEXY_ACCOUNT_NAME, NEXY_ACCOUNT_TYPE)

            // 1. Create raw contact
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, NEXY_ACCOUNT_TYPE)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, NEXY_ACCOUNT_NAME)
                    .build()
            )

            // 2. Add name
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, user.username)
                    .build()
            )

            // 3. Add phone
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            // 4. Add Nexy data
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, NEXY_MIME_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Im.DATA, user.id.toString())
                    .withValue(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM)
                    .withValue(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, "Nexy")
                    .withValue(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_WORK)
                    .withValue(ContactsContract.CommonDataKinds.Im.LABEL, user.username)
                    .build()
            )

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            Log.d(TAG, "Created new Nexy contact for ${user.username} with phone $phoneNumber")
            // Notify contacts changed
            context.contentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Nexy contact for ${user.username}", e)
            false
        }
    }

    suspend fun syncNexyUsersWithContacts(nexyUsers: List<User>): Int = withContext(Dispatchers.IO) {
        var syncedCount = 0
        nexyUsers.forEach { user ->
            if (addOrUpdateNexyContact(user)) {
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
