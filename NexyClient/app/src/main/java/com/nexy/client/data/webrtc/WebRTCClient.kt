package com.nexy.client.data.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.nexy.client.data.models.nexy.CallAnswerBody
import com.nexy.client.data.models.nexy.CallCancelBody
import com.nexy.client.data.models.nexy.CallOfferBody
import com.nexy.client.data.models.nexy.ICECandidateBody
import com.nexy.client.data.websocket.NexyWebSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class CallState {
    object Idle : CallState()
    data class Incoming(val callerId: Int, val callId: String, val sdp: String) : CallState()
    data class Outgoing(val recipientId: Int) : CallState()
    data class Active(val remoteUserId: Int) : CallState()
    object Ended : CallState()
}

@Singleton
class WebRTCClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webSocketClient: NexyWebSocketClient,
    private val gson: Gson
) {
    private val TAG = "WebRTCClient"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var currentCallId: String? = null
    private var currentRecipientId: Int? = null
    private var isInitialized = false
    
    private val eglBase = EglBase.create()
    
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    init {
        observeIncomingMessages()
    }
    
    fun initialize() {
        if (isInitialized) return
        initWebRTC()
    }

    private fun observeIncomingMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            webSocketClient.incomingMessages.collect { message ->
                if (message != null) {
                    handleSignalingMessage(message)
                }
            }
        }
    }

    private fun handleSignalingMessage(message: com.nexy.client.data.models.nexy.NexyMessage) {
        val type = message.header.type
        val body = message.body ?: return
        val senderId = message.header.senderId ?: return
        
        // We need to convert the map back to JSON and then to the specific object
        // This is a bit inefficient but works for now given the generic map structure
        val jsonBody = gson.toJson(body)
        
        when (type) {
            "call_offer" -> {
                val offer = gson.fromJson(jsonBody, CallOfferBody::class.java)
                if (currentCallId == null) {
                    Log.d(TAG, "Received call offer from $senderId: ${offer.callId}")
                    _callState.value = CallState.Incoming(senderId, offer.callId, offer.sdp)
                }
            }
            "call_answer" -> {
                val answer = gson.fromJson(jsonBody, CallAnswerBody::class.java)
                if (currentCallId == answer.callId) {
                    onRemoteSessionReceived(answer.sdp)
                    currentRecipientId?.let {
                        _callState.value = CallState.Active(it)
                    }
                }
            }
            "ice_candidate" -> {
                val candidate = gson.fromJson(jsonBody, ICECandidateBody::class.java)
                if (currentCallId == candidate.callId) {
                    onIceCandidateReceived(candidate)
                }
            }
            "call_end" -> {
                val cancel = gson.fromJson(jsonBody, com.nexy.client.data.models.nexy.CallCancelBody::class.java)
                if (currentCallId == cancel.callId) {
                    closeConnection()
                    _callState.value = CallState.Ended
                    // Reset to Idle after a short delay or immediately?
                    // For now, let UI handle the transition back to Idle or do it here
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(1000)
                        _callState.value = CallState.Idle
                    }
                }
            }
        }
    }

    private fun initWebRTC() {
        try {
            val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initializationOptions)
            
            val options = PeerConnectionFactory.Options()
            
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
                .createPeerConnectionFactory()
            
            isInitialized = true
            Log.d(TAG, "WebRTC initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
        }
    }

    fun startCall(recipientId: Int, senderId: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            // Optionally notify UI about missing permission
            return
        }

        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is not initialized, attempting to initialize")
            initialize()
            if (peerConnectionFactory == null) {
                Log.e(TAG, "Failed to initialize PeerConnectionFactory")
                return
            }
        }

        try {
            currentRecipientId = recipientId
            currentCallId = UUID.randomUUID().toString()
            _callState.value = CallState.Outgoing(recipientId)
            
            setAudioMode(true)
            createPeerConnection(senderId)
            createOffer(senderId)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call", e)
            _callState.value = CallState.Idle
        }
    }
    
    fun answerCall(senderId: Int, recipientId: Int, callId: String, remoteSdp: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is not initialized")
            return
        }

        try {
            currentRecipientId = recipientId // The caller is the recipient of our answer
            currentCallId = callId
            _callState.value = CallState.Active(recipientId)
            
            setAudioMode(true)
            createPeerConnection(senderId)
            setRemoteDescription(remoteSdp)
            createAnswer(senderId)
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
            _callState.value = CallState.Idle
        }
    }
    
    fun onRemoteSessionReceived(sdp: String) {
        setRemoteDescription(sdp)
    }
    
    fun onIceCandidateReceived(candidate: ICECandidateBody) {
        val iceCandidate = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }
    
    fun endCall(senderId: Int) {
        // Send end call message
        currentRecipientId?.let { recipientId ->
             webSocketClient.sendSignalingMessage(
                recipientId, 
                senderId, 
                "call_end", 
                mapOf("call_id" to (currentCallId ?: ""))
            )
        }
        
        closeConnection()
        _callState.value = CallState.Ended
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(1000)
            _callState.value = CallState.Idle
        }
    }

    private fun createPeerConnection(myUserId: Int) {
        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            
            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "onSignalingChange: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "onIceConnectionChange: $state")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        sendIceCandidate(it, myUserId)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {
                    Log.d(TAG, "onAddStream: ${stream?.id}")
                    // Handle remote audio stream here if needed (usually handled automatically by WebRTC for audio)
                }

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {}

                override fun onRenegotiationNeeded() {}
            })
            
            if (peerConnection == null) {
                Log.e(TAG, "Failed to create PeerConnection")
                throw RuntimeException("Failed to create PeerConnection")
            }
            
            // Create audio track
            val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
            
            // Use addTrack instead of addStream for Unified Plan
            val streamIds = listOf("ARDAMS")
            peerConnection?.addTrack(localAudioTrack, streamIds)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating peer connection", e)
            throw e
        }
    }

    private fun createOffer(senderId: Int) {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(this, it)
                    sendOffer(it.description, senderId)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { Log.e(TAG, "createOffer failed: $error") }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    private fun createAnswer(senderId: Int) {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(this, it)
                    sendAnswer(it.description, senderId)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { Log.e(TAG, "createAnswer failed: $error") }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    private fun setRemoteDescription(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp) // Or ANSWER, logic needed to distinguish
        // Simple hack: if we have currentCallId and we are not the initiator (or we are), we need to know if it's offer or answer.
        // For now, let's assume if we are calling setRemoteDescription it's the other side's SDP.
        // If we are the caller, we receive ANSWER. If we are the callee, we receive OFFER.
        
        // Better logic:
        // If peerConnection.localDescription is null, we are receiving an OFFER.
        // If peerConnection.localDescription is not null, we are receiving an ANSWER.
        
        val type = if (peerConnection?.localDescription == null) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        val desc = SessionDescription(type, sdp)
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) { Log.e(TAG, "setRemoteDescription failed: $error") }
        }, desc)
    }

    private fun sendOffer(sdp: String, senderId: Int) {
        currentRecipientId?.let { recipientId ->
            val body = CallOfferBody(
                callId = currentCallId!!,
                sdp = sdp,
                video = false,
                audio = true
            )
            webSocketClient.sendSignalingMessage(recipientId, senderId, "call_offer", body)
        }
    }

    private fun sendAnswer(sdp: String, senderId: Int) {
        currentRecipientId?.let { recipientId ->
            val body = CallAnswerBody(
                callId = currentCallId!!,
                sdp = sdp
            )
            webSocketClient.sendSignalingMessage(recipientId, senderId, "call_answer", body)
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate, senderId: Int) {
        currentRecipientId?.let { recipientId ->
            val body = ICECandidateBody(
                callId = currentCallId!!,
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
            webSocketClient.sendSignalingMessage(recipientId, senderId, "ice_candidate", body)
        }
    }
    
    private fun closeConnection() {
        peerConnection?.close()
        peerConnection = null
        localAudioTrack = null
        currentCallId = null
        currentRecipientId = null
        setAudioMode(false)
    }

    fun toggleMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleSpeaker(isSpeakerOn: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = isSpeakerOn
    }

    private fun setAudioMode(active: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (active) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false // Default to earpiece
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
    }
}
