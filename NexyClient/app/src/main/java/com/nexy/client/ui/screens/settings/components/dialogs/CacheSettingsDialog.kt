/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CacheSettingsDialog(
    currentMaxSize: Long,
    currentMaxAge: Long,
    onDismiss: () -> Unit,
    onMaxSizeChange: (Long) -> Unit,
    onMaxAgeChange: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache Settings") },
        text = {
            Column {
                Text("Max Cache Size")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMaxSize == 1024L * 1024L * 50L,
                        onClick = { onMaxSizeChange(1024L * 1024L * 50L) }
                    )
                    Text("50 MB")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMaxSize == 1024L * 1024L * 100L,
                        onClick = { onMaxSizeChange(1024L * 1024L * 100L) }
                    )
                    Text("100 MB")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMaxSize == 1024L * 1024L * 500L,
                        onClick = { onMaxSizeChange(1024L * 1024L * 500L) }
                    )
                    Text("500 MB")
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text("Auto-delete older than")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMaxAge == 1000L * 60L * 60L * 24L * 3L,
                        onClick = { onMaxAgeChange(1000L * 60L * 60L * 24L * 3L) }
                    )
                    Text("3 Days")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMaxAge == 1000L * 60L * 60L * 24L * 7L,
                        onClick = { onMaxAgeChange(1000L * 60L * 60L * 24L * 7L) }
                    )
                    Text("1 Week")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = currentMaxAge == 1000L * 60L * 60L * 24L * 30L,
                        onClick = { onMaxAgeChange(1000L * 60L * 60L * 24L * 30L) }
                    )
                    Text("1 Month")
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
