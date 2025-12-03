/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.settings.utils

import androidx.compose.ui.graphics.Color
import com.nexy.client.ui.theme.ThemeStyle

fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$size B"
    }
}

fun formatDuration(millis: Long): String {
    val days = millis / (1000 * 60 * 60 * 24)
    return "$days Days"
}

fun getThemeColor(style: ThemeStyle): Color {
    return when (style) {
        ThemeStyle.Pink -> Color(0xFFFFB7D5)
        ThemeStyle.Blue -> Color(0xFFB3C5FF)
        ThemeStyle.Green -> Color(0xFFB7F397)
        ThemeStyle.Purple -> Color(0xFFD0BCFF)
        ThemeStyle.Orange -> Color(0xFFFFB784)
        ThemeStyle.Teal -> Color(0xFF80D5D4)
    }
}
