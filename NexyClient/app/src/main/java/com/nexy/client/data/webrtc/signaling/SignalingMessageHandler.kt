package com.nexy.client.data.webrtc.signaling

import android.util.Log
import com.google.gson.Gson
import com.nexy.client.data.models.nexy.CallAnswerBody
import com.nexy.client.data.models.nexy.CallCancelBody
import com.nexy.client.data.models.nexy.CallOfferBody
import com.nexy.client.data.models.nexy.ICECandidateBody
import com.nexy.client.data.models.nexy.NexyMessage
import com.nexy.client.data.webrtc.ice.PendingCandidatesManager
import com.nexy.client.data.webrtc.models.CallState
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import javax.inject.Inject
import javax.inject.Singleton

interface SignalingListener {
    fun onRemoteSessionReceived(sdp: String)
    fun onIceCandidateReceived(candidate: ICECandidateBody)
    fun onCallEnded()
    fun getCurrentCallId(): String?
    fun setCurrentCallId(callId: String?)
    fun getCurrentRecipientId(): Int?
    fun getPeerConnection(): PeerConnection?
}

@Singleton
class SignalingMessageHandler @Inject constructor(
    private val webSocketClient: NexyWebSocketClient,
    private val pendingCandidatesManager: PendingCandidatesManager,
    private val gson: Gson
) {
    private val TAG = "SignalingHandler"
    
    private var listener: SignalingListener? = null
    private var callStateFlow: MutableStateFlow<CallState>? = null

    fun initialize(
        listener: SignalingListener,
        callStateFlow: MutableStateFlow<CallState>
    ) {
        this.listener = listener
        this.callStateFlow = callStateFlow
        observeIncomingMessages()
    }

    private fun observeIncomingMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            webSocketClient.incomingMessages.collect { message ->
                handleSignalingMessage(message)
            }
        }
    }

    private fun handleSignalingMessage(message: NexyMessage) {
        val type = message.header.type
        val body = message.body ?: return
        val senderId = message.header.senderId ?: return
        
        val jsonBody = gson.toJson(body)
        
        when (type) {
            "call_offer" -> handleCallOffer(jsonBody, senderId)
            "call_answer" -> handleCallAnswer(jsonBody)
            "ice_candidate" -> handleIceCandidate(jsonBody)
            "call_end" -> handleCallEnd(jsonBody)
        }
    }

    private fun handleCallOffer(jsonBody: String, senderId: Int) {
        val offer = gson.fromJson(jsonBody, CallOfferBody::class.java)
        if (listener?.getCurrentCallId() == null) {
            Log.d(TAG, "Received call offer from $senderId: ${offer.callId}")
            Log.d(TAG, "Offer SDP: ${offer.sdp}")
            callStateFlow?.value = CallState.Incoming(senderId, offer.callId, offer.sdp)
        }
    }

    private fun handleCallAnswer(jsonBody: String) {
        val answer = gson.fromJson(jsonBody, CallAnswerBody::class.java)
        if (listener?.getCurrentCallId() == answer.callId) {
            Log.d(TAG, "Received call answer: ${answer.sdp}")
            listener?.onRemoteSessionReceived(answer.sdp)
            listener?.getCurrentRecipientId()?.let {
                callStateFlow?.value = CallState.Active(it)
            }
        }
    }

    private fun handleIceCandidate(jsonBody: String) {
        val candidate = gson.fromJson(jsonBody, ICECandidateBody::class.java)
        val currentCallId = listener?.getCurrentCallId()
        
        when {
            currentCallId == candidate.callId -> {
                Log.d(TAG, "Received remote ICE candidate: ${candidate.candidate}")
                listener?.onIceCandidateReceived(candidate)
            }
            listener?.getPeerConnection() == null -> {
                pendingCandidatesManager.addCandidate(candidate)
            }
            else -> {
                Log.w(TAG, "Ignoring ICE candidate for different call: ${candidate.callId} (current: $currentCallId)")
            }
        }
    }

    private fun handleCallEnd(jsonBody: String) {
        val cancel = gson.fromJson(jsonBody, CallCancelBody::class.java)
        if (listener?.getCurrentCallId() == cancel.callId) {
            listener?.onCallEnded()
            callStateFlow?.value = CallState.Ended
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                callStateFlow?.value = CallState.Idle
            }
        }
    }

    fun sendOffer(recipientId: Int, senderId: Int, callId: String, sdp: String) {
        val body = CallOfferBody(
            callId = callId,
            sdp = sdp,
            video = false,
            audio = true
        )
        webSocketClient.sendSignalingMessage(recipientId, senderId, "call_offer", body)
    }

    fun sendAnswer(recipientId: Int, senderId: Int, callId: String, sdp: String) {
        val body = CallAnswerBody(
            callId = callId,
            sdp = sdp
        )
        webSocketClient.sendSignalingMessage(recipientId, senderId, "call_answer", body)
    }

    fun sendIceCandidate(recipientId: Int, senderId: Int, callId: String, candidate: org.webrtc.IceCandidate) {
        val body = ICECandidateBody(
            callId = callId,
            candidate = candidate.sdp,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex
        )
        webSocketClient.sendSignalingMessage(recipientId, senderId, "ice_candidate", body)
    }

    fun sendCallEnd(recipientId: Int, senderId: Int, callId: String) {
        webSocketClient.sendSignalingMessage(
            recipientId,
            senderId,
            "call_end",
            mapOf("call_id" to callId)
        )
    }
}
