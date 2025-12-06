package com.nexy.client.ui.screens.chat.components.voice

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexy.client.ServerConfig
import kotlinx.coroutines.delay

private const val TAG = "VoiceMessagePlayer"

@Composable
fun VoiceMessagePlayer(
    audioUrl: String?,
    duration: Int?,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var totalDuration by remember { mutableIntStateOf(duration ?: 0) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val fullUrl = remember(audioUrl) {
        if (audioUrl != null) ServerConfig.getFileUrl(audioUrl) else null
    }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying && mediaPlayer != null) {
            while (isPlaying) {
                currentPosition = mediaPlayer?.currentPosition?.div(1000) ?: 0
                delay(100)
            }
        }
    }
    
    fun startPlayback() {
        if (fullUrl == null) {
            error = "Audio URL is missing"
            return
        }
        
        isLoading = true
        error = null
        
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fullUrl)
                setOnPreparedListener { mp ->
                    isLoading = false
                    totalDuration = mp.duration / 1000
                    mp.start()
                    isPlaying = true
                    Log.d(TAG, "Playback started: $fullUrl")
                }
                setOnCompletionListener {
                    isPlaying = false
                    currentPosition = 0
                    Log.d(TAG, "Playback completed")
                }
                setOnErrorListener { _, what, extra ->
                    isLoading = false
                    isPlaying = false
                    error = "Playback error: $what"
                    Log.e(TAG, "Playback error: what=$what, extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            isLoading = false
            error = e.message
            Log.e(TAG, "Failed to start playback", e)
        }
    }
    
    fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause", e)
        }
    }
    
    fun resumePlayback() {
        try {
            mediaPlayer?.start()
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume", e)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    val backgroundColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                when {
                    isLoading -> { }
                    isPlaying -> pausePlayback()
                    mediaPlayer != null && currentPosition > 0 -> resumePlayback()
                    else -> startPlayback()
                }
            },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            val progress = if (totalDuration > 0) {
                currentPosition.toFloat() / totalDuration.toFloat()
            } else 0f
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = contentColor.copy(alpha = 0.3f)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = formatDuration(totalDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
    
    if (error != null) {
        Text(
            text = error!!,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 52.dp)
        )
    }
}
