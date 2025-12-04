/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onLogoutAllOtherSessions: () -> Unit
) {
    LaunchedEffect(Unit) {
        onLoadSessions()
    }

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

    Text(
        text = "Logged In Devices",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
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
    } else if (sessions.isEmpty()) {
        ListItem(
            headlineContent = { Text("No active sessions") },
            supportingContent = { Text("Unable to load device information") }
        )
    } else {
        sessions.forEach { session ->
            SessionItem(
                session = session,
                onLogout = { onLogoutSession(session.id) }
            )
        }

        if (sessions.size > 1) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            ListItem(
                headlineContent = { 
                    Text(
                        "Logout all other devices",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                supportingContent = { 
                    Text("End all sessions except this one") 
                },
                leadingContent = {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { onLogoutAllOtherSessions() }
            )
        }
    }
}

@Composable
private fun SessionItem(
    session: UserSession,
    onLogout: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.deviceName.ifEmpty { "Unknown Device" },
                    fontWeight = if (session.isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                if (session.isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(This device)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = { 
            Column {
                Text(
                    text = "${session.deviceType} • ${session.ipAddress}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Last active: ${formatLastActive(session.lastActive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = when {
                    session.deviceType.contains("Android", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    session.deviceType.contains("iOS", ignoreCase = true) -> Icons.Default.PhoneIphone
                    session.deviceType.contains("Web", ignoreCase = true) -> Icons.Default.Computer
                    else -> Icons.Default.Devices
                },
                contentDescription = null,
                tint = if (session.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (!session.isCurrent) {
                IconButton(onClick = { showConfirmDialog = true }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Logout Device") },
            text = { Text("Are you sure you want to logout from \"${session.deviceName}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLogout()
                        showConfirmDialog = false
                    }
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
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

private fun formatLastActive(dateString: String): String {
    return try {
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val now = java.util.Date()
        val diff = now.time - (date?.time ?: now.time)
        
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} minutes ago"
            diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
            diff < 604_800_000 -> "${diff / 86_400_000} days ago"
            else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(date!!)
        }
    } catch (e: Exception) {
        dateString
    }
}
