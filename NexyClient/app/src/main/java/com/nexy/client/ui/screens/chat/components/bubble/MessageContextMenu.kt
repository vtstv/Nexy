package com.nexy.client.ui.screens.chat.components.bubble

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun MessageContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    messageContent: String,
    isOwnMessage: Boolean,
    canDeleteMessage: Boolean = false,
    hasAttachment: Boolean,
    isDownloaded: Boolean,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
    onSaveFile: () -> Unit,
    onDownloadFile: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
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
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
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
                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Download File") },
                    onClick = {
                        onDownloadFile()
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                )
            }
        }

        if (isOwnMessage) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    onEdit()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
        }
        
        if (canDeleteMessage) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDelete()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
    }
}
