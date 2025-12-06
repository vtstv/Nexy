/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexy.client.ui.theme.ThemeStyle
import com.nexy.client.ui.screens.settings.utils.getThemeColor

@Composable
fun ChatSettingsSection(
    fontScale: Float,
    themeStyle: ThemeStyle,
    incomingTextColor: Long,
    outgoingTextColor: Long,
    showNotepad: Boolean,
    onFontScaleChange: (Float) -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    onIncomingColorClick: () -> Unit,
    onOutgoingColorClick: () -> Unit,
    onShowNotepadChange: (Boolean) -> Unit
) {
    Text(
        text = "Chat Settings",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )

    ListItem(
        headlineContent = { Text("Font Size") },
        supportingContent = { 
            Column {
                Slider(
                    value = fontScale,
                    onValueChange = onFontScaleChange,
                    valueRange = 0.7f..1.6f,
                    steps = 8
                )
                Text(
                    text = when {
                        fontScale <= 0.8f -> "Tiny"
                        fontScale <= 0.95f -> "Small"
                        fontScale <= 1.15f -> "Medium"
                        fontScale <= 1.35f -> "Large"
                        fontScale <= 1.5f -> "Extra Large"
                        else -> "Huge"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale
                    )
                )
            }
        }
    )

    ListItem(
        headlineContent = { Text("Chat Color Theme") },
        supportingContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ThemeStyle.values().forEach { style ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(getThemeColor(style))
                            .clickable { onThemeStyleChange(style) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (themeStyle == style) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    )

    ListItem(
        headlineContent = { Text("Incoming Message Text Color") },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (incomingTextColor != 0L) Color(incomingTextColor) else MaterialTheme.colorScheme.onSurface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onIncomingColorClick() }
            )
        }
    )

    ListItem(
        headlineContent = { Text("Outgoing Message Text Color") },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (outgoingTextColor != 0L) Color(outgoingTextColor) else MaterialTheme.colorScheme.onPrimaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onOutgoingColorClick() }
            )
        }
    )

    HorizontalDivider()

    ListItem(
        headlineContent = { Text("Show Notepad") },
        supportingContent = { Text("Show Notepad in chat list") },
        trailingContent = {
            Switch(
                checked = showNotepad,
                onCheckedChange = onShowNotepadChange
            )
        }
    )
}
