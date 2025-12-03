package com.nexy.client.ui.screens.chat.utils

import java.text.SimpleDateFormat
import java.util.*

fun formatTimestamp(timestamp: String?): String {
    return try {
        if (timestamp == null) return ""
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(timestamp)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date ?: Date())
    } catch (e: Exception) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}
