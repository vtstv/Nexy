package com.nexy.client.ui.screens.chat.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nexy.client.R
import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.components.voice.VoiceRecorder
import java.io.File

@Composable
fun MessageInput(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendFile: (Uri, String) -> Unit,
    onSendVoice: (File, Int) -> Unit,
    showEmojiPicker: Boolean,
    onToggleEmojiPicker: () -> Unit,
    replyToMessage: Message? = null,
    onCancelReply: () -> Unit = {},
    editingMessage: Message? = null,
    onCancelEdit: () -> Unit = {},
    voiceMessagesEnabled: Boolean = true,
    recipientVoiceMessagesEnabled: Boolean = true
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var fileName: String? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
            android.util.Log.d("MessageInput", "File selected: $fileName, URI: $uri")
            if (fileName != null) {
                onSendFile(uri, fileName!!)
            }
        }
    }
    
    Surface(tonalElevation = 3.dp) {
        Column {
            if (editingMessage != null) {
                EditPreview(message = editingMessage, onCancel = onCancelEdit)
            } else if (replyToMessage != null) {
                ReplyPreview(message = replyToMessage, onCancel = onCancelReply)
            }
            
            if (showEmojiPicker) {
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        val currentText = text.text
                        val selection = text.selection
                        
                        val newText = StringBuilder(currentText)
                            .insert(selection.start, emoji)
                            .toString()
                            
                        val newCursorPosition = selection.start + emoji.length
                        
                        onTextChange(
                            TextFieldValue(
                                text = newText,
                                selection = TextRange(newCursorPosition)
                            )
                        )
                    }
                )
            }
            
            if (isRecording) {
                VoiceRecorder(
                    onStartRecording = { file ->
                        recordingFile = file
                    },
                    onStopRecording = { file, duration ->
                        isRecording = false
                        recordingFile = null
                        onSendVoice(file, duration)
                    },
                    onCancelRecording = {
                        isRecording = false
                        recordingFile = null
                    }
                )
            } else {
                InputRow(
                    text = text,
                    onTextChange = onTextChange,
                    showEmojiPicker = showEmojiPicker,
                    onToggleEmojiPicker = onToggleEmojiPicker,
                    onAttachFile = { filePickerLauncher.launch("*/*") },
                    onStartRecording = { 
                        if (!recipientVoiceMessagesEnabled) {
                            android.widget.Toast.makeText(context, "Recipient has disabled voice messages", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            isRecording = true 
                        }
                    },
                    onSend = onSend,
                    sendEnabled = text.text.isNotBlank(),
                    isEditing = editingMessage != null,
                    voiceMessagesEnabled = voiceMessagesEnabled
                )
            }
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
                    text = "Replying to ${message.sender?.displayName ?: message.sender?.username ?: "User"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content ?: "",
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
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    showEmojiPicker: Boolean,
    onToggleEmojiPicker: () -> Unit,
    onAttachFile: () -> Unit,
    onStartRecording: () -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    isEditing: Boolean = false,
    voiceMessagesEnabled: Boolean = true
) {
    val hasText = text.text.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleEmojiPicker,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.EmojiEmotions, stringResource(R.string.add_emoji))
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .heightIn(min = 46.dp),
            placeholder = { Text(stringResource(R.string.message_placeholder)) },
            maxLines = 4,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            trailingIcon = {
                AnimatedVisibility(
                    visible = !hasText,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = onAttachFile,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, stringResource(R.string.attach_file))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.width(4.dp))

        if (!hasText && !isEditing && voiceMessagesEnabled) {
            FilledIconButton(
                onClick = onStartRecording,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Record voice message"
                )
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (isEditing) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                    if (isEditing) "Save" else stringResource(R.string.send)
                )
            }
        }
    }
}

@Composable
private fun EditPreview(
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
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Editing message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Edit")
            }
        }
    }
}
