package com.nexy.client.ui.screens.chat.components.bubble

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.DpOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun MessageContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    messageContent: String,
    messageId: String,
    chatId: Int,
    serverMessageId: Int?,
    isOwnMessage: Boolean,
    canDeleteMessage: Boolean = false,
    canPinMessage: Boolean = false,
    hasAttachment: Boolean,
    isDownloaded: Boolean,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit = {},
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
    onSaveFile: () -> Unit,
    onDownloadFile: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val messageLink = "https://nexy.app/chat/$chatId/message/${serverMessageId ?: messageId}"

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 72.dp)
    ) {
        DropdownMenuItem(
            text = { Text("Reply") },
            onClick = {
                onReply()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) }
        )
        
        DropdownMenuItem(
            text = { Text("Copy") },
            onClick = {
                clipboardManager.setText(AnnotatedString(messageContent))
                onCopy()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
        )

        DropdownMenuItem(
            text = { Text("Copy Link") },
            onClick = {
                clipboardManager.setText(AnnotatedString(messageLink))
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) }
        )

        DropdownMenuItem(
            text = { Text("Forward") },
            onClick = {
                val shareText = messageContent.ifBlank { messageLink }
                if (shareText.isNotEmpty()) {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    val chooser = Intent.createChooser(sendIntent, "Forward message")
                    context.startActivity(chooser)
                }
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Filled.Forward, contentDescription = null) }
        )

        if (hasAttachment) {
            if (isDownloaded) {
                DropdownMenuItem(
                    text = { Text("Open File") },
                    onClick = {
                        onOpenFile()
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Save to Downloads") },
                    onClick = {
                        onSaveFile()
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Download File") },
                    onClick = {
                        onDownloadFile()
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) }
                )
            }
        }

        if (canPinMessage) {
            DropdownMenuItem(
                text = { Text("Pin") },
                onClick = {
                    onPin()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) }
            )
        }

        if (isOwnMessage) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    onEdit()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
            )
        }
        
        if (canDeleteMessage) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDelete()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }
            )
        }
    }
}
