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
fun SecuritySection(
    pinCode: String?,
    isBiometricEnabled: Boolean,
    canAuthenticate: Boolean,
    onPinToggle: (Boolean) -> Unit,
    onBiometricToggle: (Boolean) -> Unit
) {
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
}
