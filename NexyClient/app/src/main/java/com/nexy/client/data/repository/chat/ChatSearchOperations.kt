package com.nexy.client.data.repository.chat

import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.models.Chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSearchOperations @Inject constructor(
    private val apiService: NexyApiService
) {
    suspend fun searchPublicGroups(query: String): Result<List<Chat>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchPublicGroups(query)
                if (response.isSuccessful) {
                    Result.success(response.body() ?: emptyList())
                } else {
                    Result.failure(Exception("Failed to search groups"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
