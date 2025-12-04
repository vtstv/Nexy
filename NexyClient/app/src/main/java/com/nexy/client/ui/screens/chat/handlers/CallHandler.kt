package com.nexy.client.ui.screens.chat.handlers

import com.nexy.client.data.webrtc.WebRTCClient
import javax.inject.Inject

class CallHandler @Inject constructor(
    private val webRTCClient: WebRTCClient
) {
    fun startCall(participantIds: List<Int>, currentUserId: Int?): String? {
        if (currentUserId == null || participantIds.isEmpty()) {
            return "Chat info not loaded"
        }
        
        val recipientId = participantIds.firstOrNull { it != currentUserId }
        if (recipientId == null) {
            return "Cannot find recipient for call"
        }
        
        try {
            webRTCClient.startCall(recipientId, currentUserId)
            return null
        } catch (e: Exception) {
            return "Failed to start call: ${e.message}"
        }
    }
}
