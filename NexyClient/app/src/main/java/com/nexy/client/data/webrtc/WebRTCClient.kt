package com.nexy.client.data.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexy.client.data.models.nexy.ICECandidateBody
import com.nexy.client.data.webrtc.audio.AudioModeManager
import com.nexy.client.data.webrtc.config.IceServerConfig
import com.nexy.client.data.webrtc.connection.PeerConnectionManager
import com.nexy.client.data.webrtc.ice.IceCandidateFilter
import com.nexy.client.data.webrtc.ice.PendingCandidatesManager
import com.nexy.client.data.webrtc.models.CallState
import com.nexy.client.data.webrtc.models.CallStats
import com.nexy.client.data.webrtc.signaling.SignalingListener
import com.nexy.client.data.webrtc.signaling.SignalingMessageHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peerConnectionManager: PeerConnectionManager,
    private val signalingHandler: SignalingMessageHandler,
    private val pendingCandidatesManager: PendingCandidatesManager,
    private val audioModeManager: AudioModeManager,
    private val iceServerConfig: IceServerConfig
) : SignalingListener {
    private val TAG = "WebRTCClient"
    
    private var currentCallId: String? = null
    private var currentRecipientId: Int? = null

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callStats = MutableStateFlow(CallStats())
    val callStats: StateFlow<CallStats> = _callStats.asStateFlow()

    init {
        signalingHandler.initialize(this, _callState)
        peerConnectionManager.setCallStatsFlow(_callStats)
        iceServerConfig.fetchIceServers { servers ->
            peerConnectionManager.updateIceServers(servers)
        }
    }

    fun initialize() {
        if (peerConnectionManager.isInitialized) return
        peerConnectionManager.initialize()
    }

    fun startCall(recipientId: Int, senderId: Int) {
        if (!hasAudioPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        if (!peerConnectionManager.isInitialized) {
            Log.e(TAG, "PeerConnectionFactory not initialized, attempting to initialize")
            initialize()
            if (!peerConnectionManager.isInitialized) {
                Log.e(TAG, "Failed to initialize PeerConnectionFactory")
                return
            }
        }

        try {
            currentRecipientId = recipientId
            currentCallId = UUID.randomUUID().toString()
            _callState.value = CallState.Outgoing(recipientId)
            
            audioModeManager.setCallActive(true)
            peerConnectionManager.createPeerConnection(senderId) { candidate ->
                sendIceCandidate(candidate, senderId)
            }
            createOffer(senderId)
            peerConnectionManager.startStatsLogging()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call", e)
            _callState.value = CallState.Idle
        }
    }

    fun answerCall(senderId: Int, recipientId: Int, callId: String, remoteSdp: String) {
        if (!hasAudioPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        if (!peerConnectionManager.isInitialized) {
            Log.e(TAG, "PeerConnectionFactory not initialized")
            return
        }

        try {
            currentRecipientId = recipientId
            currentCallId = callId
            _callState.value = CallState.Active(recipientId)
            
            audioModeManager.setCallActive(true)
            peerConnectionManager.createPeerConnection(senderId) { candidate ->
                sendIceCandidate(candidate, senderId)
            }
            peerConnectionManager.setRemoteDescription(remoteSdp)
            createAnswer(senderId)
            peerConnectionManager.startStatsLogging()
            
            processPendingIceCandidates()
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
            _callState.value = CallState.Idle
        }
    }

    fun endCall(senderId: Int) {
        currentRecipientId?.let { recipientId ->
            currentCallId?.let { callId ->
                signalingHandler.sendCallEnd(recipientId, senderId, callId)
            }
        }
        
        closeConnection()
        _callState.value = CallState.Ended
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            _callState.value = CallState.Idle
        }
    }

    fun toggleMute(isMuted: Boolean) {
        Log.d(TAG, "toggleMute: $isMuted")
        peerConnectionManager.toggleMute(isMuted)
    }

    fun toggleSpeaker(isSpeakerOn: Boolean) {
        Log.d(TAG, "toggleSpeaker: $isSpeakerOn")
        audioModeManager.setSpeakerphoneOn(isSpeakerOn)
    }

    // SignalingListener implementation
    override fun onRemoteSessionReceived(sdp: String) {
        peerConnectionManager.setRemoteDescription(sdp)
    }

    override fun onIceCandidateReceived(candidate: ICECandidateBody) {
        Log.d(TAG, "Processing remote ICE candidate: ${candidate.candidate}")
        
        val filterResult = IceCandidateFilter.shouldAllowCandidate(candidate)
        if (filterResult is IceCandidateFilter.FilterResult.Blocked) {
            return
        }
        
        val iceCandidate = IceCandidateFilter.toWebRtcCandidate(candidate)
        peerConnectionManager.addIceCandidate(iceCandidate)
    }

    override fun onCallEnded() {
        closeConnection()
    }

    override fun getCurrentCallId(): String? = currentCallId

    override fun setCurrentCallId(callId: String?) {
        currentCallId = callId
    }

    override fun getCurrentRecipientId(): Int? = currentRecipientId

    override fun getPeerConnection(): PeerConnection? = peerConnectionManager.getPeerConnection()

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun createOffer(senderId: Int) {
        peerConnectionManager.createOffer { desc ->
            currentRecipientId?.let { recipientId ->
                currentCallId?.let { callId ->
                    signalingHandler.sendOffer(recipientId, senderId, callId, desc.description)
                }
            }
        }
    }

    private fun createAnswer(senderId: Int) {
        peerConnectionManager.createAnswer { desc ->
            currentRecipientId?.let { recipientId ->
                currentCallId?.let { callId ->
                    signalingHandler.sendAnswer(recipientId, senderId, callId, desc.description)
                }
            }
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate, senderId: Int) {
        currentRecipientId?.let { recipientId ->
            currentCallId?.let { callId ->
                signalingHandler.sendIceCandidate(recipientId, senderId, callId, candidate)
            }
        }
    }

    private fun processPendingIceCandidates() {
        val callId = currentCallId ?: return
        val candidates = pendingCandidatesManager.getAndClearCandidatesForCall(callId)
        
        candidates.forEach { candidate ->
            Log.d(TAG, "Processing queued ICE candidate: ${candidate.candidate}")
            onIceCandidateReceived(candidate)
        }
    }

    private fun closeConnection() {
        peerConnectionManager.close()
        currentCallId = null
        currentRecipientId = null
        audioModeManager.setCallActive(false)
        pendingCandidatesManager.clear()
    }
}
