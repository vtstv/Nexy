package com.nexy.client.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object DeepLinkHandler {
    
    fun handleDeepLink(context: Context, uri: Uri): DeepLinkAction? {
        return when (uri.scheme) {
            "nexy" -> {
                when (uri.host) {
                    "invite" -> {
                        val code = uri.pathSegments.firstOrNull()
                        code?.let { DeepLinkAction.JoinByInvite(it) }
                    }
                    "user" -> {
                        val userId = uri.pathSegments.firstOrNull()
                        val username = uri.getQueryParameter("username")
                        if (userId != null) {
                            DeepLinkAction.ViewUser(userId, username)
                        } else null
                    }
                    else -> null
                }
            }
            else -> null
        }
    }
    
    fun createInviteIntent(inviteCode: String): Intent {
        val uri = Uri.parse("nexy://invite/$inviteCode")
        return Intent(Intent.ACTION_VIEW, uri)
    }
    
    fun createUserIntent(userId: String, username: String?): Intent {
        val uriBuilder = Uri.parse("nexy://user/$userId").buildUpon()
        username?.let { uriBuilder.appendQueryParameter("username", it) }
        return Intent(Intent.ACTION_VIEW, uriBuilder.build())
    }
}

sealed class DeepLinkAction {
    data class JoinByInvite(val code: String) : DeepLinkAction()
    data class ViewUser(val userId: String, val username: String?) : DeepLinkAction()
}
