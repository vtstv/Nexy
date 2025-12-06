package com.nexy.client.data.api

import com.nexy.client.data.local.AuthTokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenManager: AuthTokenManager
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = runBlocking { 
            tokenManager.getAccessToken() 
        }
        
        val deviceId = runBlocking {
            tokenManager.getDeviceId()
        }
        
        val newRequest = originalRequest.newBuilder()
            .apply {
                // Always add device ID header
                header("X-Device-ID", deviceId)
                
                // Add auth token if available (skip for login/register)
                val url = originalRequest.url.toString()
                if (token != null && token.isNotEmpty() && 
                    !url.contains("/auth/login") && !url.contains("/auth/register")) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        
        return chain.proceed(newRequest)
    }
}
