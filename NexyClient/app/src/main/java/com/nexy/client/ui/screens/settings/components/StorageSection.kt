/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.ui.screens.settings.utils.formatDuration
import com.nexy.client.ui.screens.settings.utils.formatFileSize

@Composable
fun StorageSection(
    cacheSize: Long,
    cacheMaxSize: Long,
    cacheMaxAge: Long,
    onClearCache: () -> Unit,
    onCacheSettingsClick: () -> Unit
) {
    Text(
        text = "Storage & Cache",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
    
    ListItem(
        headlineContent = { Text("Current Cache Size") },
        supportingContent = { Text(formatFileSize(cacheSize)) },
        trailingContent = {
            Button(onClick = onClearCache) {
                Text("Clear")
            }
        }
    )
    
    ListItem(
        headlineContent = { Text("Max Cache Size") },
        supportingContent = { Text(formatFileSize(cacheMaxSize)) },
        modifier = Modifier.clickable { onCacheSettingsClick() }
    )
    
    ListItem(
        headlineContent = { Text("Auto-delete older than") },
        supportingContent = { Text(formatDuration(cacheMaxAge)) },
        modifier = Modifier.clickable { onCacheSettingsClick() }
    )
}
