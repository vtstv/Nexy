package com.nexy.client.ui.components

import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

sealed class LinkType {
    data class InviteLink(val code: String) : LinkType()
    data class UserLink(val userId: String) : LinkType()
    data class MessageLink(val chatId: String, val messageId: String) : LinkType()
    data class WebLink(val url: String) : LinkType()
}

object LinkParser {
    private val NEXY_INVITE_PATTERN = Regex("""nexy://invite/([A-Za-z0-9_-]+)""")
    private val NEXY_USER_PATTERN = Regex("""nexy://user/(\d+)""")
    private val NEXY_MESSAGE_PATTERN = Regex("""nexy://chat/(\d+)/message/([A-Za-z0-9_-]+)""")
    private val URL_PATTERN = Regex("""https?://[^\s]+""")

    fun findLinks(text: String): List<Pair<IntRange, LinkType>> {
        val links = mutableListOf<Pair<IntRange, LinkType>>()
        
        NEXY_INVITE_PATTERN.findAll(text).forEach { match ->
            val code = match.groupValues[1]
            links.add(match.range to LinkType.InviteLink(code))
        }
        
        NEXY_USER_PATTERN.findAll(text).forEach { match ->
            val userId = match.groupValues[1]
            links.add(match.range to LinkType.UserLink(userId))
        }
        
        NEXY_MESSAGE_PATTERN.findAll(text).forEach { match ->
            val chatId = match.groupValues[1]
            val messageId = match.groupValues[2]
            links.add(match.range to LinkType.MessageLink(chatId, messageId))
        }
        
        URL_PATTERN.findAll(text).forEach { match ->
            val existingLink = links.any { it.first.overlaps(match.range) }
            if (!existingLink) {
                links.add(match.range to LinkType.WebLink(match.value))
            }
        }
        
        return links.sortedBy { it.first.first }
    }

    fun extractInviteCode(text: String): String? {
        return NEXY_INVITE_PATTERN.find(text)?.groupValues?.get(1)
    }

    fun extractInviteCodeFromUri(uri: Uri): String? {
        if (uri.scheme == "nexy" && uri.host == "invite") {
            return uri.pathSegments.firstOrNull()
        }
        return null
    }

    fun extractMessageLink(text: String): Pair<String, String>? {
        val match = NEXY_MESSAGE_PATTERN.find(text)
        return if (match != null) {
            Pair(match.groupValues[1], match.groupValues[2])
        } else null
    }

    private fun IntRange.overlaps(other: IntRange): Boolean {
        return maxOf(this.first, other.first) <= minOf(this.last, other.last)
    }
}

@Composable
fun LinkedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onInviteLinkClick: (String) -> Unit = {},
    onUserLinkClick: (String) -> Unit = {},
    onMessageLinkClick: (String, String) -> Unit = { _, _ -> },
    onWebLinkClick: (String) -> Unit = {}
) {
    val links = LinkParser.findLinks(text)
    
    if (links.isEmpty()) {
        androidx.compose.material3.Text(
            text = text,
            style = style,
            modifier = modifier
        )
        return
    }
    
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        
        links.forEach { (range, linkType) ->
            if (currentIndex < range.first) {
                append(text.substring(currentIndex, range.first))
            }
            
            val tag = when (linkType) {
                is LinkType.InviteLink -> "INVITE"
                is LinkType.UserLink -> "USER"
                is LinkType.MessageLink -> "MESSAGE"
                is LinkType.WebLink -> "WEB"
            }
            
            val annotation = when (linkType) {
                is LinkType.InviteLink -> linkType.code
                is LinkType.UserLink -> linkType.userId
                is LinkType.MessageLink -> "${linkType.chatId}|${linkType.messageId}"
                is LinkType.WebLink -> linkType.url
            }
            
            pushStringAnnotation(tag = tag, annotation = annotation)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(text.substring(range))
            }
            pop()
            
            currentIndex = range.last + 1
        }
        
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { annotation ->
                when (annotation.tag) {
                    "INVITE" -> onInviteLinkClick(annotation.item)
                    "USER" -> onUserLinkClick(annotation.item)
                    "MESSAGE" -> {
                        val parts = annotation.item.split("|")
                        if (parts.size == 2) {
                            onMessageLinkClick(parts[0], parts[1])
                        }
                    }
                    "WEB" -> onWebLinkClick(annotation.item)
                }
            }
        }
    )
}
