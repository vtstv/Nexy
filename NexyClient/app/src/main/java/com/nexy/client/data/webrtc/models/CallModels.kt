package com.nexy.client.data.webrtc.models

sealed class CallState {
    object Idle : CallState()
    data class Incoming(val callerId: Int, val callId: String, val sdp: String) : CallState()
    data class Outgoing(val recipientId: Int) : CallState()
    data class Active(val remoteUserId: Int) : CallState()
    object Ended : CallState()
}

data class CallStats(
    val inboundBytes: String = "0",
    val inboundPackets: String = "0",
    val outboundBytes: String = "0",
    val outboundPackets: String = "0",
    val iceState: String = "UNKNOWN",
    val signalingState: String = "UNKNOWN"
)
