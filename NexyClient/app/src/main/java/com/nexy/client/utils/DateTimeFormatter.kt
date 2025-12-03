package com.nexy.client.utils

import java.text.SimpleDateFormat
import java.util.*

object DateTimeFormatter {
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    fun formatMessageTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> timeFormat.format(Date(timestamp))
            diff < 604800_000 -> {
                val days = diff / 86400_000
                "$days day${if (days > 1) "s" else ""} ago"
            }
            else -> dateFormat.format(Date(timestamp))
        }
    }
    
    fun formatFullDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }
    
    fun formatChatListTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Now"
            diff < 3600_000 -> "${diff / 60_000}m"
            diff < 86400_000 -> timeFormat.format(Date(timestamp))
            else -> dateFormat.format(Date(timestamp))
        }
    }
    
    fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val messageTime = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        
        return now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)
    }
    
    fun isYesterday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val messageTime = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        
        now.add(Calendar.DAY_OF_YEAR, -1)
        
        return now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)
    }
}
