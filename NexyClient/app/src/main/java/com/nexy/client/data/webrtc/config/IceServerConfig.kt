package com.nexy.client.data.webrtc.config

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IceServerConfig @Inject constructor(
    private val apiService: NexyApiService
) {
    private val TAG = "IceServerConfig"
    
    private var iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    fun getIceServers(): List<PeerConnection.IceServer> = iceServers

    fun fetchIceServers(onComplete: (List<PeerConnection.IceServer>) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getICEServers()
                if (response.isSuccessful) {
                    response.body()?.let { config ->
                        iceServers = config.iceServers.map { iceServer ->
                            Log.d(TAG, "Adding ICE server: ${iceServer.urls.joinToString()}")
                            val builder = PeerConnection.IceServer.builder(iceServer.urls)
                            if (iceServer.username != null && iceServer.credential != null) {
                                builder.setUsername(iceServer.username)
                                builder.setPassword(iceServer.credential)
                            }
                            builder.createIceServer()
                        }
                        Log.d(TAG, "ICE servers configured: ${iceServers.size} servers")
                        onComplete(iceServers)
                    }
                } else {
                    Log.w(TAG, "Failed to fetch ICE servers, using defaults")
                    onComplete(iceServers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching ICE servers, using defaults", e)
                onComplete(iceServers)
            }
        }
    }
}
