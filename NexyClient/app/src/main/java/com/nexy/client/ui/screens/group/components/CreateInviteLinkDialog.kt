/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class InviteLinkSettings(
    val usageLimit: Int?,
    val expiresInSeconds: Int?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInviteLinkDialog(
    onDismiss: () -> Unit,
    onCreate: (InviteLinkSettings) -> Unit
) {
    var usageLimitEnabled by remember { mutableStateOf(false) }
    var usageLimit by remember { mutableStateOf("10") }
    var expirationEnabled by remember { mutableStateOf(false) }
    var selectedExpiration by remember { mutableStateOf(ExpirationOption.ONE_DAY) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Create Invite Link",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Usage limit setting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Limit number of uses",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Link will be disabled after reaching limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = usageLimitEnabled,
                        onCheckedChange = { usageLimitEnabled = it }
                    )
                }
                
                if (usageLimitEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usageLimit,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.all { it.isDigit() }) {
                                usageLimit = value
                            }
                        },
                        label = { Text("Maximum uses") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Expiration setting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set expiration time",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Link will expire after specified time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = expirationEnabled,
                        onCheckedChange = { expirationEnabled = it }
                    )
                }
                
                if (expirationEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpirationSelector(
                        selected = selectedExpiration,
                        onSelect = { selectedExpiration = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val settings = InviteLinkSettings(
                                usageLimit = if (usageLimitEnabled) usageLimit.toIntOrNull() else null,
                                expiresInSeconds = if (expirationEnabled) selectedExpiration.seconds else null
                            )
                            onCreate(settings)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

enum class ExpirationOption(val label: String, val seconds: Int) {
    ONE_HOUR("1 hour", 3600),
    SIX_HOURS("6 hours", 21600),
    ONE_DAY("1 day", 86400),
    THREE_DAYS("3 days", 259200),
    ONE_WEEK("1 week", 604800),
    ONE_MONTH("1 month", 2592000)
}

@Composable
private fun ExpirationSelector(
    selected: ExpirationOption,
    onSelect: (ExpirationOption) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        ExpirationOption.values().forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
