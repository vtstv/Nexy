package com.nexy.client.e2e

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * API client for E2E operations
 */
class E2EApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val gson: Gson
) {
    
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    /**
     * Upload encryption keys to server
     */
    suspend fun uploadKeys(token: String, request: KeyUploadRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(request)
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/e2e/keys")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            response.use {
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get user's Key Bundle
     */
    suspend fun getKeyBundle(
        token: String,
        userId: Int,
        deviceId: Int = 1
    ): Result<KeyBundle> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/e2e/keys/bundle?user_id=$userId&device_id=$deviceId")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = client.newCall(httpRequest).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                
                val json = response.body?.string()
                val bundleResponse = gson.fromJson(json, KeyBundleResponse::class.java)
                Result.success(bundleResponse.bundle)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check number of available PreKeys
     */
    suspend fun checkPreKeyCount(token: String): Result<PreKeyCountResponse> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/e2e/keys/prekey-count")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = client.newCall(httpRequest).execute()
            response.use {
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                
                val json = response.body?.string()
                val result = gson.fromJson(json, PreKeyCountResponse::class.java)
                Result.success(result)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
