package com.nexy.client.data.models.nexy

import com.google.gson.annotations.SerializedName

data class CallOfferBody(
    @SerializedName("call_id") val callId: String,
    @SerializedName("sdp") val sdp: String,
    @SerializedName("video") val video: Boolean,
    @SerializedName("audio") val audio: Boolean
)

data class CallAnswerBody(
    @SerializedName("call_id") val callId: String,
    @SerializedName("sdp") val sdp: String
)

data class ICECandidateBody(
    @SerializedName("call_id") val callId: String,
    @SerializedName("candidate") val candidate: String,
    @SerializedName("sdp_mid") val sdpMid: String?,
    @SerializedName("sdp_m_line_index") val sdpMLineIndex: Int
)

data class CallCancelBody(
    @SerializedName("call_id") val callId: String,
    @SerializedName("reason") val reason: String
)
