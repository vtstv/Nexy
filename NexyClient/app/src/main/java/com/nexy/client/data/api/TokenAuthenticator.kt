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

	companion object {
		private const val REFRESH_FAILURE_COOLDOWN_MS = 60_000L
	}

	@Volatile
	private var refreshFailureUntilMs: Long = 0L
    
    override fun authenticate(route: Route?, response: Response): Request? {
		// Avoid infinite loops: never try to refresh while we're already calling refresh.
		val path = response.request.url.encodedPath
		if (path.contains("/auth/refresh")) {
			return null
		}

		// If refresh is failing, don't hammer the server on every request.
		val now = System.currentTimeMillis()
		if (now < refreshFailureUntilMs) {
			return null
		}

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

				// Keep user "logged in" locally: do NOT clear tokens here.
				// Just back off to avoid spamming refresh requests.
				refreshFailureUntilMs = System.currentTimeMillis() + REFRESH_FAILURE_COOLDOWN_MS
            }
        }
        
        return null
    }
}
