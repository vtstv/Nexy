package com.nexy.client.data.webrtc.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AudioModeManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun setCallActive(active: Boolean) {
        if (active) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
        Log.d(TAG, "setCallActive: active=$active, mode=${audioManager.mode}, speaker=${audioManager.isSpeakerphoneOn}")
    }

    fun setSpeakerphoneOn(enabled: Boolean) {
        Log.d(TAG, "setSpeakerphoneOn: $enabled")
        audioManager.isSpeakerphoneOn = enabled
    }

    fun isSpeakerphoneOn(): Boolean = audioManager.isSpeakerphoneOn
}
