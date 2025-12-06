package com.nexy.client.ui.screens.chat.components.voice

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "VoiceRecorder"
private const val MAX_DURATION_SECONDS = 300 // 5 minutes

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceRecorder(
    onStartRecording: (File) -> Unit,
    onStopRecording: (File, Int) -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    fun startRecording() {
        if (!audioPermissionState.status.isGranted) {
            return
        }
        
        if (recorder != null) return

        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            recordingDuration = 0
            onStartRecording(file)
            Log.d(TAG, "Recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onCancelRecording()
        }
    }
    
    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            
            outputFile?.let { file ->
                if (file.exists()) {
                    Log.d(TAG, "Recording stopped: duration=$recordingDuration, file=${file.absolutePath}")
                    onStopRecording(file, recordingDuration)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            // Even if stop fails, try to save what we have if file exists
             outputFile?.let { file ->
                if (file.exists()) {
                    onStopRecording(file, recordingDuration)
                } else {
                    onCancelRecording()
                }
            }
        }
    }
    
    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            
            outputFile?.delete()
            outputFile = null
            recordingDuration = 0
            onCancelRecording()
            Log.d(TAG, "Recording cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel recording", e)
            onCancelRecording()
        }
    }

    // Handle permissions and auto-start
    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        } else {
            startRecording()
        }
    }

    // Start recording when permission is granted
    LaunchedEffect(audioPermissionState.status.isGranted) {
        if (audioPermissionState.status.isGranted) {
            startRecording()
        }
    }
    
    // Timer and auto-stop
    LaunchedEffect(recorder) {
        if (recorder != null) {
            while (recordingDuration < MAX_DURATION_SECONDS) {
                delay(1000)
                recordingDuration++
            }
            if (recordingDuration >= MAX_DURATION_SECONDS) {
                stopRecording()
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing recorder", e)
            }
            recorder = null
        }
    }
    
    RecordingUI(
        duration = recordingDuration,
        pulseScale = pulseScale,
        onStop = ::stopRecording,
        onCancel = ::cancelRecording,
        modifier = modifier
    )
}

@Composable
private fun RecordingUI(
    duration: Int,
    pulseScale: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.error
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        IconButton(
            onClick = onStop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Stop recording",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}
