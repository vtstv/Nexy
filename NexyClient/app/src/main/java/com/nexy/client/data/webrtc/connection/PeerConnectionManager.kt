package com.nexy.client.data.webrtc.connection

import android.content.Context
import android.util.Log
import com.nexy.client.data.webrtc.models.CallStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton

interface PeerConnectionListener {
    fun onIceCandidate(candidate: IceCandidate)
    fun onIceConnectionChange(state: PeerConnection.IceConnectionState?)
    fun onSignalingChange(state: PeerConnection.SignalingState?)
}

@Singleton
class PeerConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "PeerConnectionManager"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var statsJob: Job? = null
    private var listener: PeerConnectionListener? = null
    private var callStatsFlow: MutableStateFlow<CallStats>? = null
    
    private val eglBase = EglBase.create()
    
    private var iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    val isInitialized: Boolean get() = peerConnectionFactory != null

    fun setListener(listener: PeerConnectionListener) {
        this.listener = listener
    }

    fun setCallStatsFlow(flow: MutableStateFlow<CallStats>) {
        this.callStatsFlow = flow
    }

    fun updateIceServers(servers: List<PeerConnection.IceServer>) {
        this.iceServers = servers
        Log.d(TAG, "ICE servers configured: ${servers.size} servers")
    }

    fun initialize() {
        if (peerConnectionFactory != null) return
        
        try {
            val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initializationOptions)
            
            val options = PeerConnectionFactory.Options()
            
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
                .createPeerConnectionFactory()
            
            Log.d(TAG, "WebRTC initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
        }
    }

    fun createPeerConnection(myUserId: Int, onIceCandidate: (IceCandidate) -> Unit): PeerConnection? {
        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "PeerConnectionFactory not initialized")
            return null
        }

        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                iceTransportsType = PeerConnection.IceTransportsType.ALL
            }
            Log.d(TAG, "🌐 ICE configured: ALL candidates with smart filtering")

            peerConnection = factory.createPeerConnection(rtcConfig, createPeerConnectionObserver(onIceCandidate))

            if (peerConnection == null) {
                Log.e(TAG, "Failed to create PeerConnection")
                throw RuntimeException("Failed to create PeerConnection")
            }

            val audioSource = factory.createAudioSource(MediaConstraints())
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
            
            val streamIds = listOf("ARDAMS")
            peerConnection?.addTrack(localAudioTrack, streamIds)

            return peerConnection
        } catch (e: Exception) {
            Log.e(TAG, "Error creating peer connection", e)
            throw e
        }
    }

    private fun createPeerConnectionObserver(onIceCandidate: (IceCandidate) -> Unit) = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "onSignalingChange: $state")
            callStatsFlow?.value = callStatsFlow?.value?.copy(signalingState = state.toString()) ?: CallStats()
            listener?.onSignalingChange(state)
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "onIceConnectionChange: $state")
            callStatsFlow?.value = callStatsFlow?.value?.copy(iceState = state.toString()) ?: CallStats()
            listener?.onIceConnectionChange(state)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "onIceGatheringChange: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "onIceCandidate: ${it.sdpMid}:${it.sdpMLineIndex} - ${it.sdp}")
                onIceCandidate(it)
                listener?.onIceCandidate(it)
            }
        }

        override fun onIceCandidateError(event: IceCandidateErrorEvent) {
            Log.e(TAG, "onIceCandidateError: ${event.errorCode} - ${event.errorText} at ${event.address}:${event.port} url: ${event.url}")
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

        override fun onAddStream(stream: MediaStream?) {
            Log.d(TAG, "onAddStream: ${stream?.id}")
        }

        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    fun createOffer(onSuccess: (SessionDescription) -> Unit) {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(this, it)
                    onSuccess(it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { Log.e(TAG, "createOffer failed: $error") }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    fun createAnswer(onSuccess: (SessionDescription) -> Unit) {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(this, it)
                    onSuccess(it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { Log.e(TAG, "createAnswer failed: $error") }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    fun setRemoteDescription(sdp: String) {
        val type = if (peerConnection?.localDescription == null) 
            SessionDescription.Type.OFFER 
        else 
            SessionDescription.Type.ANSWER
        
        val desc = SessionDescription(type, sdp)
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) { Log.e(TAG, "setRemoteDescription failed: $error") }
        }, desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMute(isMuted: Boolean) {
        Log.d(TAG, "toggleMute: $isMuted")
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun startStatsLogging() {
        stopStatsLogging()
        val statsFlow = callStatsFlow ?: return
        
        statsJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(1000)
                peerConnection?.getStats { report ->
                    var stats = statsFlow.value

                    for (stat in report.statsMap.values) {
                        if (stat.type == "inbound-rtp" && stat.members["kind"] == "audio") {
                            stats = stats.copy(
                                inboundBytes = stat.members["bytesReceived"]?.toString() ?: stats.inboundBytes,
                                inboundPackets = stat.members["packetsReceived"]?.toString() ?: stats.inboundPackets
                            )
                            Log.d(TAG, "Stats Inbound: bytes=${stats.inboundBytes}, packets=${stats.inboundPackets}")
                        }
                        if (stat.type == "outbound-rtp" && stat.members["kind"] == "audio") {
                            stats = stats.copy(
                                outboundBytes = stat.members["bytesSent"]?.toString() ?: stats.outboundBytes,
                                outboundPackets = stat.members["packetsSent"]?.toString() ?: stats.outboundPackets
                            )
                            Log.d(TAG, "Stats Outbound: bytes=${stats.outboundBytes}, packets=${stats.outboundPackets}")
                        }
                    }
                    statsFlow.value = stats
                }
            }
        }
    }

    fun stopStatsLogging() {
        statsJob?.cancel()
        statsJob = null
    }

    fun close() {
        stopStatsLogging()
        peerConnection?.close()
        peerConnection = null
        localAudioTrack = null
    }

    fun getPeerConnection(): PeerConnection? = peerConnection
}
