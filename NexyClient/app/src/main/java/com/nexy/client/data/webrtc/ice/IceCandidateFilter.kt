package com.nexy.client.data.webrtc.ice

import android.util.Log
import com.nexy.client.data.models.nexy.ICECandidateBody
import org.webrtc.IceCandidate

object IceCandidateFilter {
    private const val TAG = "IceCandidateFilter"

    sealed class FilterResult {
        object Allowed : FilterResult()
        data class Blocked(val reason: String) : FilterResult()
    }

    fun shouldAllowCandidate(candidate: ICECandidateBody): FilterResult {
        return shouldAllowCandidate(candidate.candidate)
    }

    fun shouldAllowCandidate(candidateStr: String): FilterResult {
        val isLoopback = candidateStr.contains("127.0.0.1") || candidateStr.contains("::1")
        val isEmulatorInternal = candidateStr.contains("10.0.2.")
        val isDockerNetwork = Regex("""172\.(1[6-9]|2[0-9]|3[01])\..*""").containsMatchIn(candidateStr)
        val isLocalNetwork = candidateStr.contains("192.168.")
        val isRelay = candidateStr.contains("typ relay")
        val isHost = candidateStr.contains("typ host")
        val isSrflx = candidateStr.contains("typ srflx")

        return when {
            isLoopback || isEmulatorInternal -> {
                Log.w(TAG, "❌ Blocking unreachable: $candidateStr")
                FilterResult.Blocked("Unreachable address")
            }
            isDockerNetwork && !isRelay -> {
                Log.w(TAG, "❌ Blocking Docker network: $candidateStr")
                FilterResult.Blocked("Docker network not allowed")
            }
            isLocalNetwork && (isHost || isSrflx) -> {
                Log.d(TAG, "✅ Allowing LAN candidate: $candidateStr")
                FilterResult.Allowed
            }
            isRelay -> {
                Log.d(TAG, "✅ Allowing relay candidate: $candidateStr")
                FilterResult.Allowed
            }
            else -> {
                Log.d(TAG, "✅ Allowing candidate: $candidateStr")
                FilterResult.Allowed
            }
        }
    }

    fun toWebRtcCandidate(candidate: ICECandidateBody): IceCandidate {
        return IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
    }
}
