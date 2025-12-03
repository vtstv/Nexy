package com.nexy.client.data.api

import com.google.gson.JsonParser
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.models.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: AuthTokenManager,
    private val apiService: dagger.Lazy<NexyApiService>
) : Authenticator {
    
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code == 401) {
            val refreshToken = runBlocking { tokenManager.getRefreshToken() }
            
            if (refreshToken != null) {
                val newTokenResponse = runBlocking {
                    try {
                        apiService.get().refreshToken(RefreshTokenRequest(refreshToken))
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (newTokenResponse?.isSuccessful == true) {
                    val authResponse = newTokenResponse.body()
                    if (authResponse != null) {
                        runBlocking {
                            tokenManager.saveTokens(
                                authResponse.accessToken,
                                authResponse.refreshToken
                            )
                        }
                        
                        return response.request.newBuilder()
                            .header("Authorization", "Bearer ${authResponse.accessToken}")
                            .build()
                    }
                }
                
                runBlocking { tokenManager.clearTokens() }
            }
        }
        
        return null
    }
}
