package com.nexy.client.ui.screens.chat.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nexy.client.R

import com.nexy.client.data.models.Message
import androidx.compose.material.icons.automirrored.filled.Reply

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendFile: (Uri, String) -> Unit,
    showEmojiPicker: Boolean,
    onToggleEmojiPicker: () -> Unit,
    replyToMessage: Message? = null,
    onCancelReply: () -> Unit = {}
) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        selectedFileName = it.getString(nameIndex)
                    }
                }
            }
            android.util.Log.d("MessageInput", "File selected: $selectedFileName, URI: $uri")
        }
    }
    
    Surface(tonalElevation = 3.dp) {
        Column {
            replyToMessage?.let { message ->
                ReplyPreview(
                    message = message,
                    onCancel = onCancelReply
                )
            }

            selectedFileUri?.let { uri ->
                FilePreview(
                    fileName = selectedFileName ?: "File",
                    onRemove = {
                        selectedFileUri = null
                        selectedFileName = null
                    }
                )
            }
            
            if (showEmojiPicker) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        onTextChange(text + emoji)
                    }
                )
            }
            
            InputRow(
                text = text,
                onTextChange = onTextChange,
                showEmojiPicker = showEmojiPicker,
                onToggleEmojiPicker = onToggleEmojiPicker,
                onAttachFile = { filePickerLauncher.launch("*/*") },
                onSend = {
                    val fileUri = selectedFileUri
                    val fileName = selectedFileName
                    if (fileUri != null && fileName != null) {
                        onSendFile(fileUri, fileName)
                        selectedFileUri = null
                        selectedFileName = null
                    } else {
                        onSend()
                    }
                },
                sendEnabled = text.isNotBlank() || selectedFileUri != null
            )
        }
    }
}

@Composable
private fun ReplyPreview(
    message: Message,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Reply",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replying to ${message.sender?.displayName ?: "User"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel reply")
            }
        }
    }
}

@Composable
private fun FilePreview(
    fileName: String,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "File",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "Remove file")
            }
        }
    }
}

@Composable
private fun EmojiPicker(
    onEmojiSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        val emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
            "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍"
        )
        LazyColumn(
            modifier = Modifier
                .height(240.dp)
                .padding(8.dp)
        ) {
            items(emojis.chunked(8)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { emoji ->
                        TextButton(
                            onClick = { onEmojiSelected(emoji) },
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    text: String,
    onTextChange: (String) -> Unit,
    showEmojiPicker: Boolean,
    onToggleEmojiPicker: () -> Unit,
    onAttachFile: () -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onAttachFile,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.AttachFile, stringResource(R.string.attach_file))
        }
        
        IconButton(
            onClick = onToggleEmojiPicker,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.EmojiEmotions, stringResource(R.string.add_emoji))
        }
        
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            placeholder = { Text(stringResource(R.string.message_placeholder)) },
            maxLines = 4,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
        
        IconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, 
                stringResource(R.string.send),
                tint = if (sendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}
