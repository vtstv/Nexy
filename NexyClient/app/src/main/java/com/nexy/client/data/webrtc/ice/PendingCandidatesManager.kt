package com.nexy.client.data.webrtc.ice

import android.util.Log
import com.nexy.client.data.models.nexy.ICECandidateBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingCandidatesManager @Inject constructor() {
    private val TAG = "PendingCandidates"
    private val pendingCandidates = mutableListOf<ICECandidateBody>()

    @Synchronized
    fun addCandidate(candidate: ICECandidateBody) {
        Log.d(TAG, "Queuing early ICE candidate for call ${candidate.callId}: ${candidate.candidate}")
        pendingCandidates.add(candidate)
    }

    @Synchronized
    fun getCandidatesForCall(callId: String): List<ICECandidateBody> {
        return pendingCandidates.filter { it.callId == callId }
    }

    @Synchronized
    fun getAndClearCandidatesForCall(callId: String, onOtherCandidates: (Int) -> Unit = {}): List<ICECandidateBody> {
        if (pendingCandidates.isEmpty()) {
            Log.d(TAG, "No pending ICE candidates to process")
            return emptyList()
        }

        Log.d(TAG, "Processing ${pendingCandidates.size} pending ICE candidates")
        val candidatesToProcess = pendingCandidates.filter { it.callId == callId }
        val otherCandidatesCount = pendingCandidates.count { it.callId != callId }

        if (otherCandidatesCount > 0) {
            Log.w(TAG, "Discarding $otherCandidatesCount candidates for different calls")
            onOtherCandidates(otherCandidatesCount)
        }

        pendingCandidates.clear()
        return candidatesToProcess
    }

    @Synchronized
    fun clear() {
        pendingCandidates.clear()
    }

    @Synchronized
    fun isEmpty(): Boolean = pendingCandidates.isEmpty()
}
