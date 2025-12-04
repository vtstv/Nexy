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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.ui.theme.ThemeStyle
import com.nexy.client.ui.screens.settings.utils.getThemeColor

@Composable
fun AppearanceSection(
    isDarkTheme: Boolean,
    themeStyle: ThemeStyle,
    onToggleTheme: () -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit
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
            Column {
                listOf("en" to "English", "de" to "Deutsch", "ru" to "Russian").forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
