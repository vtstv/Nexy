/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File

class VoicePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(file: File, onCompletion: () -> Unit = {}) {
        stop()
        try {
            mediaPlayer = MediaPlayer.create(context, Uri.fromFile(file))
            mediaPlayer?.setOnCompletionListener { 
                stop()
                onCompletion()
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("VoicePlayer", "play(File) failed", e)
        }
    }
    
    fun play(uri: Uri, onCompletion: () -> Unit = {}) {
        stop()
        try {
            mediaPlayer = MediaPlayer.create(context, uri)
            mediaPlayer?.setOnCompletionListener { 
                stop()
                onCompletion()
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("VoicePlayer", "play(Uri) failed", e)
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
