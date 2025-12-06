/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.UserSession

@Composable
fun SecuritySection(
    pinCode: String?,
    isBiometricEnabled: Boolean,
    canAuthenticate: Boolean,
    sessions: List<UserSession>,
    isLoadingSessions: Boolean,
    onPinToggle: (Boolean) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLoadSessions: () -> Unit,
    onLogoutSession: (Int) -> Unit,
    onLogoutAllOtherSessions: () -> Unit,
    onUpdateSessionSettings: (sessionId: Int, acceptSecretChats: Boolean?, acceptCalls: Boolean?) -> Unit = { _, _, _ -> }
) {
    var showTerminateAllDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        onLoadSessions()
    }

    // Security section header
    Text(
        text = "Security",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
    
    ListItem(
        headlineContent = { Text("App PIN Code") },
        supportingContent = { Text(if (pinCode != null) "Enabled" else "Disabled") },
        trailingContent = {
            Switch(
                checked = pinCode != null,
                onCheckedChange = onPinToggle
            )
        }
    )
    
    if (pinCode != null && canAuthenticate) {
        ListItem(
            headlineContent = { Text("Biometric Unlock") },
            supportingContent = { Text("Use fingerprint or face unlock") },
            trailingContent = {
                Switch(
                    checked = isBiometricEnabled,
                    onCheckedChange = onBiometricToggle
                )
            }
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // ========== THIS DEVICE SECTION ==========
    // Server marks current device by device_id, so we can trust isCurrent flag
    val currentSession = sessions.find { it.isCurrent }
    val otherSessions = sessions.filter { !it.isCurrent }

    Text(
        text = "This device",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    if (isLoadingSessions) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else if (currentSession != null) {
        CurrentDeviceItem(
            session = currentSession,
            onUpdateSessionSettings = onUpdateSessionSettings
        )
        
        // Terminate all other sessions button 
        if (otherSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { 
                    Text(
                        "Terminate all other sessions",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                },
                supportingContent = { 
                    Text(
                        "Logs out all devices except for this one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                modifier = Modifier.clickable { showTerminateAllDialog = true }
            )
        }
    } else {
        ListItem(
            headlineContent = { Text("No active session") },
            supportingContent = { Text("Unable to load device information") }
        )
    }

    // ========== ACTIVE DEVICES SECTION ==========
    if (otherSessions.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
            text = "Active Devices",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        otherSessions.forEach { session ->
            OtherDeviceItem(
                session = session,
                onLogout = { onLogoutSession(session.id) }
            )
        }
    }

    // Terminate all dialog
    if (showTerminateAllDialog) {
        AlertDialog(
            onDismissRequest = { showTerminateAllDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Terminate all other sessions?") },
            text = { 
                Text("This will log you out from all devices except this one. Are you sure?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLogoutAllOtherSessions()
                        showTerminateAllDialog = false
                    }
                ) {
                    Text("Terminate", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTerminateAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CurrentDeviceItem(
    session: UserSession,
    onUpdateSessionSettings: (sessionId: Int, acceptSecretChats: Boolean?, acceptCalls: Boolean?) -> Unit
) {
    var acceptSecretChats by remember(session.acceptSecretChats) { mutableStateOf(session.acceptSecretChats) }
    var acceptCalls by remember(session.acceptCalls) { mutableStateOf(session.acceptCalls) }

    ListItem(
        headlineContent = { 
            Text(
                text = session.deviceName.ifEmpty { "Unknown Device" },
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = { 
            Column {
                Text(
                    text = "Nexy ${session.deviceType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = extractDeviceModel(session.deviceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            DeviceIcon(
                deviceType = session.deviceType,
                isCurrentDevice = true
            )
        }
    )
    
    // Session settings for current device
    ListItem(
        headlineContent = { Text("Accept Secret Chats") },
        supportingContent = { Text("Allow encrypted chats on this device") },
        trailingContent = {
            Switch(
                checked = acceptSecretChats,
                onCheckedChange = { checked ->
                    acceptSecretChats = checked
                    onUpdateSessionSettings(session.id, checked, null)
                }
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
    
    ListItem(
        headlineContent = { Text("Accept Calls") },
        supportingContent = { Text("Allow incoming calls on this device") },
        trailingContent = {
            Switch(
                checked = acceptCalls,
                onCheckedChange = { checked ->
                    acceptCalls = checked
                    onUpdateSessionSettings(session.id, null, checked)
                }
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun OtherDeviceItem(
    session: UserSession,
    onLogout: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { 
            Text(
                text = session.deviceName.ifEmpty { "Unknown Device" },
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = { 
            Column {
                Text(
                    text = "Nexy ${session.deviceType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${extractDeviceModel(session.deviceId)} • ${formatLastActive(session.lastActive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            DeviceIcon(
                deviceType = session.deviceType,
                isCurrentDevice = false
            )
        },
        trailingContent = {
            IconButton(onClick = { showConfirmDialog = true }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Terminate session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Terminate session?") },
            text = { 
                Text("Are you sure you want to terminate the session on \"${session.deviceName}\"?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLogout()
                        showConfirmDialog = false
                    }
                ) {
                    Text("Terminate", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceIcon(
    deviceType: String,
    isCurrentDevice: Boolean
) {
    val backgroundColor = if (isCurrentDevice) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color(0xFF4CAF50).copy(alpha = 0.2f) // Green
    }
    
    val iconColor = if (isCurrentDevice) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0xFF4CAF50) // Green
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                deviceType.contains("Android", ignoreCase = true) -> Icons.Default.PhoneAndroid
                deviceType.contains("iOS", ignoreCase = true) -> Icons.Default.PhoneIphone
                deviceType.contains("Desktop", ignoreCase = true) -> Icons.Default.Computer
                deviceType.contains("Web", ignoreCase = true) -> Icons.Default.Language
                else -> Icons.Default.Devices
            },
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun formatLastActive(dateString: String): String {
    return try {
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val now = java.util.Date()
        val diff = now.time - (date?.time ?: now.time)
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> timeFormat.format(date!!) // Today - show time
            diff < 604_800_000 -> "${diff / 86_400_000} days ago"
            else -> dateFormat.format(date!!) // Show date
        }
    } catch (e: Exception) {
        dateString
    }
}

/**
 * Extract device model from device_id.
 * Device ID format: "Android-{MODEL}-{ANDROID_ID}-{UUID}"
 * Example: "Android-SM-S901E-d19984e93f258d95-62a55c07-416f-4419-b417-1a3831c9daba"
 * Returns: "SM-S901E"
 */
private fun extractDeviceModel(deviceId: String): String {
    return try {
        val parts = deviceId.split("-")
        // Device ID starts with "Android-{MODEL}-..."
        // For model "SM-S901E", after split we get: ["Android", "SM", "S901E", "d19984e93f258d95", ...]
        // We need to join parts[1] and parts[2] if parts[2] looks like part of model (starts with letter or digit, not long hex)
        if (parts.size >= 3 && parts[0] == "Android") {
            val firstPart = parts[1]
            val secondPart = parts.getOrNull(2) ?: ""
            
            // If second part is short (< 10 chars) and alphanumeric, it's likely part of model name
            // Otherwise it's android_id (long hex string like "d19984e93f258d95")
            if (secondPart.length < 10 && secondPart.isNotEmpty() && secondPart[0].isLetterOrDigit()) {
                "$firstPart-$secondPart"
            } else {
                firstPart
            }
        } else {
            "Unknown"
        }
    } catch (e: Exception) {
        "Unknown"
    }
}
