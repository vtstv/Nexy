package com.nexy.client.data.contacts

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexy.client.data.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactsSyncService: ContactsSyncService,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "ContactsSyncManager"
        const val ACCOUNT_TYPE = "com.nexy.client"
        const val ACCOUNT_NAME = "Nexy"
    }
    
    fun ensureNexyAccountExists(): Boolean {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        
        if (accounts.isEmpty()) {
            val account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)
            return try {
                val added = accountManager.addAccountExplicitly(account, null, null)
                if (added) {
                    ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
                    ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
                    Log.d(TAG, "Nexy account created successfully")
                }
                added
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Nexy account", e)
                false
            }
        }
        return true
    }
    
    suspend fun syncDeviceContactsWithNexy(): SyncResult = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) {
            Log.d(TAG, "No contacts permission")
            return@withContext SyncResult.NoPermission
        }
        
        ensureNexyAccountExists()
        
        try {
            // Clear old Nexy entries first to recreate with proper aggregation
            val cleared = contactsSyncService.removeAllNexyIndicators()
            Log.d(TAG, "Cleared $cleared old Nexy entries")
            
            val devicePhoneNumbers = contactsSyncService.getDeviceContactPhoneNumbers()
            Log.d(TAG, "Found ${devicePhoneNumbers.size} device contacts with phone numbers")
            
            if (devicePhoneNumbers.isEmpty()) {
                return@withContext SyncResult.Success(0)
            }
            
            val nexyUsersResult = userRepository.syncContacts(devicePhoneNumbers)
            nexyUsersResult.fold(
                onSuccess = { nexyUsers ->
                    Log.d(TAG, "Found ${nexyUsers.size} Nexy users matching device contacts")
                    if (nexyUsers.isEmpty()) {
                        SyncResult.Success(0)
                    } else {
                        val syncedCount = contactsSyncService.syncNexyUsersWithContacts(nexyUsers)
                        Log.d(TAG, "Synced $syncedCount contacts with Nexy indicator")
                        SyncResult.Success(syncedCount)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Sync contacts API failed", error)
                    SyncResult.Error(error.message ?: "Sync failed")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            SyncResult.Error(e.message ?: "Sync failed")
        }
    }
    
    suspend fun clearNexyIndicators(): Int = withContext(Dispatchers.IO) {
        contactsSyncService.removeAllNexyIndicators()
    }
    
    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    sealed class SyncResult {
        data class Success(val syncedCount: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }
}
