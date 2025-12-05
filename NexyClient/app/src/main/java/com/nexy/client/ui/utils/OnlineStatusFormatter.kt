package com.nexy.client.ui.utils

object OnlineStatusFormatter {

    fun formatOnlineStatus(onlineStatus: String?, lastSeen: String? = null): String {
        return when {
            onlineStatus.isNullOrEmpty() -> ""
            onlineStatus == "online" -> "online"
            onlineStatus == "last seen recently" -> "last seen recently"
            onlineStatus == "last seen within a week" -> "last seen within a week"
            onlineStatus == "last seen within a month" -> "last seen within a month"
            onlineStatus == "last seen a long time ago" -> "last seen a long time ago"
            else -> onlineStatus
        }
    }
    
    fun isOnline(onlineStatus: String?): Boolean {
        return onlineStatus == "online"
    }
}
