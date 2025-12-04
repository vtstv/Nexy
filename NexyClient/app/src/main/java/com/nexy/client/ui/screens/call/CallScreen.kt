package com.nexy.client.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.data.webrtc.CallState
import com.nexy.client.data.webrtc.WebRTCClient

@Composable
fun CallScreen(
    viewModel: CallViewModel = hiltViewModel(),
    currentUserId: Int,
    onDismiss: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val callStats by viewModel.callStats.collectAsState()
    val remoteUser by viewModel.remoteUser.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var showDebugInfo by remember { mutableStateOf(false) }

    // Handle state changes
    LaunchedEffect(callState) {
        if (callState is CallState.Idle || callState is CallState.Ended) {
            // Wait a bit before dismissing if it was ended
            if (callState is CallState.Ended) {
                kotlinx.coroutines.delay(1000)
            }
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Status and User Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 64.dp)
            ) {
                // Debug Info Toggle
                IconButton(
                    onClick = { showDebugInfo = !showDebugInfo },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Debug Info",
                        tint = if (showDebugInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showDebugInfo) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("ICE State: ${callStats.iceState}", fontSize = 12.sp)
                            Text("Signaling: ${callStats.signalingState}", fontSize = 12.sp)
                            Text("Inbound: ${callStats.inboundBytes} bytes / ${callStats.inboundPackets} pkts", fontSize = 12.sp)
                            Text("Outbound: ${callStats.outboundBytes} bytes / ${callStats.outboundPackets} pkts", fontSize = 12.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = when (val state = callState) {
                        is CallState.Incoming -> "Incoming Call..."
                        is CallState.Outgoing -> "Calling..."
                        is CallState.Active -> "Connected"
                        is CallState.Ended -> "Call Ended"
                        else -> ""
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                if (callState is CallState.Active) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatDuration(callDuration),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = remoteUser?.displayName ?: remoteUser?.username ?: when (val state = callState) {
                        is CallState.Incoming -> "User ${state.callerId}"
                        is CallState.Outgoing -> "User ${state.recipientId}"
                        is CallState.Active -> "User ${state.remoteUserId}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom section: Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val state = callState) {
                    is CallState.Incoming -> {
                        // Decline Button
                        IconButton(
                            onClick = { viewModel.endCall(currentUserId) },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Red, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                "Decline",
                                tint = Color.White
                            )
                        }
                        
                        // Accept Button
                        IconButton(
                            onClick = { 
                                viewModel.answerCall(
                                    currentUserId, 
                                    state.callerId, 
                                    state.callId, 
                                    state.sdp
                                ) 
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Green, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Call,
                                "Accept",
                                tint = Color.White
                            )
                        }
                    }
                    
                    is CallState.Outgoing, is CallState.Active -> {
                        // Speaker Button
                        IconButton(
                            onClick = { 
                                isSpeakerOn = !isSpeakerOn
                                viewModel.toggleSpeaker(isSpeakerOn)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                "Speaker",
                                tint = if (isSpeakerOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Mute Button
                        IconButton(
                            onClick = { 
                                isMuted = !isMuted
                                viewModel.toggleMute(isMuted)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                "Mute",
                                tint = if (isMuted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // End Call Button
                        IconButton(
                            onClick = { viewModel.endCall(currentUserId) },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Red, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    else -> {}
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
