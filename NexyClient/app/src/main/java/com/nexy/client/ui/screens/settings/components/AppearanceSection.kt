/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexy.client.R
import com.nexy.client.ui.theme.ThemeStyle
import com.nexy.client.ui.screens.settings.utils.getThemeColor
import kotlin.math.roundToInt

@Composable
fun AppearanceSection(
    isDarkTheme: Boolean,
    themeStyle: ThemeStyle,
    uiScale: Float,
    onToggleTheme: () -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    onUiScaleChange: (Float) -> Unit
) {
    Text(
        text = stringResource(R.string.appearance),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )

    ListItem(
        headlineContent = { Text(if (isDarkTheme) stringResource(R.string.dark_theme) else stringResource(R.string.light_theme)) },
        supportingContent = { Text("Toggle between light and dark mode") },
        leadingContent = {
            Icon(
                if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null
            )
        },
        trailingContent = {
            Switch(
                checked = isDarkTheme,
                onCheckedChange = { onToggleTheme() }
            )
        }
    )

    HorizontalDivider()

    ListItem(
        headlineContent = { Text(stringResource(R.string.theme_style)) },
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

    HorizontalDivider()

    var showLanguageSelector by remember { mutableStateOf(false) }
    val currentLanguage = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        when {
            locales.isEmpty -> "System"
            else -> when (locales.toLanguageTags()) {
                "en" -> "English"
                "de" -> "Deutsch"
                "ru" -> "Russian"
                else -> "System"
            }
        }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.language)) },
        supportingContent = { Text(currentLanguage) },
        modifier = Modifier.clickable { showLanguageSelector = true }
    )

    if (showLanguageSelector) {
        LanguageSelectorDialog(
            onDismiss = { showLanguageSelector = false },
            onLanguageSelected = { code ->
                val localeList = LocaleListCompat.forLanguageTags(code)
                AppCompatDelegate.setApplicationLocales(localeList)
                showLanguageSelector = false
            }
        )
    }

    HorizontalDivider()

    // UI Scale setting
    var tempScale by remember(uiScale) { mutableStateOf(uiScale) }
    
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ZoomIn,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.ui_scale),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${(tempScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = { 
                    tempScale = 1.0f
                    onUiScaleChange(1.0f)
                },
                enabled = tempScale != 1.0f
            ) {
                Text(stringResource(R.string.reset))
            }
        }
        
        Slider(
            value = tempScale,
            onValueChange = { tempScale = it },
            onValueChangeFinished = { onUiScaleChange(tempScale) },
            valueRange = 0.8f..1.4f,
            steps = 5,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Preview elements scaled by tempScale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size((40 * tempScale).dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            fontSize = (16 * tempScale).sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width((12 * tempScale).dp))
                    Column {
                        Text(
                            text = "Chat Name",
                            fontSize = (16 * tempScale).sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Last message preview...",
                            fontSize = (14 * tempScale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectorDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column(
                modifier = Modifier.width(IntrinsicSize.Min)
            ) {
                listOf("en" to "English", "de" to "Deutsch", "ru" to "Русский").forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = false,
                            onClick = { onLanguageSelected(code) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
