/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@Composable
fun NotificationsSection(
    pushNotificationsEnabled: Boolean,
    notificationSoundEnabled: Boolean,
    notificationSoundUri: String?,
    notificationVibrationEnabled: Boolean,
    isBackgroundServiceEnabled: Boolean,
    onPushNotificationsChange: (Boolean) -> Unit,
    onNotificationSoundChange: (Boolean) -> Unit,
    onNotificationVibrationChange: (Boolean) -> Unit,
    onBackgroundServiceChange: (Boolean) -> Unit,
    ringtoneLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val context = LocalContext.current
    
    Text(
        text = "Notifications",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )

    ListItem(
        headlineContent = { Text("Push Notifications") },
        supportingContent = { Text("Receive notifications for new messages") },
        trailingContent = {
            Switch(
                checked = pushNotificationsEnabled,
                onCheckedChange = onPushNotificationsChange
            )
        }
    )

    ListItem(
        headlineContent = { Text("Sound") },
        supportingContent = { Text("Play sound for incoming messages") },
        trailingContent = {
            Switch(
                checked = notificationSoundEnabled,
                onCheckedChange = onNotificationSoundChange,
                enabled = pushNotificationsEnabled
            )
        }
    )
    
    if (notificationSoundEnabled && pushNotificationsEnabled) {
        val currentSoundName = remember(notificationSoundUri) {
            try {
                if (notificationSoundUri == null) {
                    "Default"
                } else {
                    RingtoneManager.getRingtone(context, Uri.parse(notificationSoundUri))
                        .getTitle(context)
                }
            } catch (e: Exception) {
                "Unknown"
            }
        }
        
        ListItem(
            headlineContent = { Text("Notification Sound") },
            supportingContent = { Text(currentSoundName) },
            modifier = Modifier.clickable {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    
                    val existingUri = if (notificationSoundUri != null) {
                        Uri.parse(notificationSoundUri)
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    }
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                }
                ringtoneLauncher.launch(intent)
            }
        )
    }

    ListItem(
        headlineContent = { Text("Vibration") },
        supportingContent = { Text("Vibrate for incoming messages") },
        trailingContent = {
            Switch(
                checked = notificationVibrationEnabled,
                onCheckedChange = onNotificationVibrationChange,
                enabled = pushNotificationsEnabled
            )
        }
    )

    ListItem(
        headlineContent = { Text("Background Service") },
        supportingContent = { Text("Keep connection alive in background (uses more battery)") },
        trailingContent = {
            Switch(
                checked = isBackgroundServiceEnabled,
                onCheckedChange = onBackgroundServiceChange
            )
        }
    )
}
