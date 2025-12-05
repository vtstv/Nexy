package com.nexy.client.ui.screens.profile.userprofile

import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun formatLastSeen(dateString: String): String {
    return try {
        val parsedDate = ZonedDateTime.parse(dateString)
        val now = ZonedDateTime.now()
        val minutes = ChronoUnit.MINUTES.between(parsedDate, now)
        
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10080 -> "${minutes / 1440}d ago"
            else -> "recently"
        }
    } catch (e: DateTimeParseException) {
        "recently"
    }
}
