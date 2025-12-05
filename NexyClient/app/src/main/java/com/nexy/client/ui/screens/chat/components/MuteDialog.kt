package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MuteDialog(
    onDismiss: () -> Unit,
    onMute: (duration: String?, until: String?) -> Unit
) {
    var selectedOption by remember { mutableStateOf("1h") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute Notifications") },
        text = {
            Column {
                MuteOption("1 Hour", "1h", selectedOption) { selectedOption = it }
                MuteOption("1 Day", "1d", selectedOption) { selectedOption = it }
                MuteOption("1 Month", "1m", selectedOption) { selectedOption = it }
                MuteOption("Forever", "forever", selectedOption) { selectedOption = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onMute(selectedOption, null)
                }
            ) {
                Text("Mute")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MuteOption(
    label: String,
    value: String,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (value == selectedValue),
            onClick = { onSelect(value) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}
