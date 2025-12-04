package com.nexy.client.ui.screens.chat.utils

import java.text.SimpleDateFormat
import java.util.*

fun formatTimestamp(timestamp: String?): String {
    return try {
        if (timestamp == null) return ""
        // Try parsing with ISO format first (handling potential Z or offsets if needed, but keeping simple for now)
        // The server seems to send "2025-01-01T12:00:00Z" or similar.
        // We'll try a few patterns if the first fails, or just stick to the one that works.
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        
        var date: Date? = null
        for (format in formats) {
            try {
                val parser = SimpleDateFormat(format, Locale.getDefault())
                parser.timeZone = TimeZone.getTimeZone("UTC") // Assuming server sends UTC
                date = parser.parse(timestamp)
                if (date != null) break
            } catch (e: Exception) {
                continue
            }
        }
        
        if (date == null) return ""
        
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}

fun formatDateHeader(timestamp: String?): String {
    if (timestamp == null) return ""
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        
        var date: Date? = null
        for (format in formats) {
            try {
                val parser = SimpleDateFormat(format, Locale.getDefault())
                parser.timeZone = TimeZone.getTimeZone("UTC")
                date = parser.parse(timestamp)
                if (date != null) break
            } catch (e: Exception) {
                continue
            }
        }
        
        if (date == null) return ""
        
        // Check if it's this year
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        calendar.time = date
        val msgYear = calendar.get(Calendar.YEAR)
        
        val pattern = if (currentYear == msgYear) "MMMM d" else "MMMM d, yyyy"
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    } catch (e: Exception) {
        ""
    }
}

fun isSameDay(timestamp1: String?, timestamp2: String?): Boolean {
    if (timestamp1 == null || timestamp2 == null) return false
    val header1 = formatDateHeader(timestamp1)
    val header2 = formatDateHeader(timestamp2)
    return header1 == header2 && header1.isNotEmpty()
}
