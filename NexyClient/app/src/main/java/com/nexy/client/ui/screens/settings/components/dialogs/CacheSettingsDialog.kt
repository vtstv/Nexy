/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CacheSettingsDialog(
    currentMaxSize: Long,
    currentMaxAge: Long,
    onDismiss: () -> Unit,
    onMaxSizeChange: (Long) -> Unit,
    onMaxAgeChange: (Long) -> Unit
) {
    // Convert bytes to MB for slider (100MB to 10GB = 100 to 10240)
    var sliderSize by remember { mutableFloatStateOf((currentMaxSize / (1024f * 1024f)).coerceIn(100f, 10240f)) }
    
    // Convert millis to days for slider (7 days to 365 days)
    var sliderAge by remember { mutableFloatStateOf((currentMaxAge / (1000f * 60f * 60f * 24f)).coerceIn(7f, 365f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache Settings") },
        text = {
            Column {
                Text(
                    text = "Max Cache Size: ${formatSize(sliderSize)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderSize,
                    onValueChange = { 
                        sliderSize = it
                        onMaxSizeChange((it * 1024 * 1024).toLong())
                    },
                    valueRange = 100f..10240f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100 MB", style = MaterialTheme.typography.bodySmall)
                    Text("10 GB", style = MaterialTheme.typography.bodySmall)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "Auto-delete older than: ${formatAge(sliderAge)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderAge,
                    onValueChange = { 
                        sliderAge = it
                        onMaxAgeChange((it * 24 * 60 * 60 * 1000).toLong())
                    },
                    valueRange = 7f..365f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 Week", style = MaterialTheme.typography.bodySmall)
                    Text("1 Year", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun formatSize(mb: Float): String {
    return if (mb >= 1000) {
        String.format("%.1f GB", mb / 1024)
    } else {
        "${mb.roundToInt()} MB"
    }
}

private fun formatAge(days: Float): String {
    val d = days.roundToInt()
    return when {
        d < 30 -> "$d Days"
        d < 365 -> "${d / 30} Months"
        else -> "1 Year"
    }
}
