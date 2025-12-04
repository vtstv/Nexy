package com.nexy.client.ui.screens.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexy.client.data.models.User
import com.nexy.client.data.repository.UserRepository
import com.nexy.client.data.webrtc.CallState
import com.nexy.client.data.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val webRTCClient: WebRTCClient,
    private val userRepository: UserRepository
) : ViewModel() {

    val callState = webRTCClient.callState

    private val _remoteUser = MutableStateFlow<User?>(null)
    val remoteUser: StateFlow<User?> = _remoteUser.asStateFlow()

    init {
        viewModelScope.launch {
            callState.collectLatest { state ->
                val remoteUserId = when (state) {
                    is CallState.Incoming -> state.callerId
                    is CallState.Outgoing -> state.recipientId
                    is CallState.Active -> state.remoteUserId
                    else -> null
                }

                if (remoteUserId != null) {
                    fetchUser(remoteUserId)
                } else {
                    _remoteUser.value = null
                }
            }
        }
    }

    private fun fetchUser(userId: Int) {
        viewModelScope.launch {
            val result = userRepository.getUserById(userId)
            if (result.isSuccess) {
                _remoteUser.value = result.getOrNull()
            }
        }
    }
    
    fun endCall(currentUserId: Int) {
        webRTCClient.endCall(currentUserId)
    }
    
    fun answerCall(currentUserId: Int, callerId: Int, callId: String, sdp: String) {
        webRTCClient.answerCall(currentUserId, callerId, callId, sdp)
    }
    
    fun toggleSpeaker(isSpeakerOn: Boolean) {
        webRTCClient.toggleSpeaker(isSpeakerOn)
    }
    
    fun toggleMute(isMuted: Boolean) {
        webRTCClient.toggleMute(isMuted)
    }
}
