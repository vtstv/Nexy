/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VoiceMediaSection(
    voiceMessagesEnabled: Boolean,
    onVoiceMessagesChange: (Boolean) -> Unit
) {
    Text(
        text = "Voice & Media",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
    
    ListItem(
        headlineContent = { Text("Voice Messages") },
        supportingContent = { Text("Enable voice message recording and playback") },
        trailingContent = {
            Switch(
                checked = voiceMessagesEnabled,
                onCheckedChange = onVoiceMessagesChange
            )
        }
    )
}
