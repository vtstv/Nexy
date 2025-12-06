package com.nexy.client.ui.screens.chat.components.bubble

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageType
import com.nexy.client.ui.screens.chat.components.voice.VoiceMessagePlayer
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageAttachment(
    message: Message,
    onOpenFile: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val imageUrl = ServerConfig.getFileUrl(message.mediaUrl)
    AsyncImage(
        model = imageUrl,
        contentDescription = "Image",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { onOpenFile(message.content ?: "") },
                onLongClick = onLongClick
            ),
        contentScale = ContentScale.Crop
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoAttachment(
    message: Message,
    isDownloaded: Boolean,
    onOpenFile: (String) -> Unit,
    onDownloadFile: (String, String) -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .combinedClickable(
                onClick = {
                    if (isDownloaded) {
                        onOpenFile(message.content ?: "")
                    } else {
                        val fileId = message.mediaUrl?.substringAfterLast("/") ?: ""
                        if (fileId.isNotEmpty()) {
                            onDownloadFile(fileId, message.content ?: "")
                        }
                    }
                },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = "Play Video",
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
        if (!isDownloaded) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenericFileAttachment(
    message: Message,
    isDownloaded: Boolean,
    onOpenFile: (String) -> Unit,
    onDownloadFile: (String, String) -> Unit,
    onSaveFile: (String) -> Unit,
    onLongClick: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .combinedClickable(
                    onClick = {
                        if (isDownloaded) {
                            onOpenFile(message.content ?: "")
                        } else {
                            val fileId = message.mediaUrl?.substringAfterLast("/") ?: ""
                            if (fileId.isNotEmpty()) {
                                onDownloadFile(fileId, message.content ?: "")
                            }
                        }
                    },
                    onLongClick = onLongClick
                )
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = "File",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.content ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (isDownloaded) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "OPEN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = { onSaveFile(message.content ?: "") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save to Downloads",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = message.mediaType ?: "File",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileAttachment(
    message: Message,
    isOwnMessage: Boolean = false,
    onDownloadFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val file = File(context.getExternalFilesDir(null), message.content ?: "")
    val isDownloaded = file.exists()

    val isImage = message.mediaType?.startsWith("image/") == true
    val isVideo = message.mediaType?.startsWith("video/") == true
    val isVoice = message.type == MessageType.VOICE

    when {
        isVoice -> VoiceMessagePlayer(
            audioUrl = message.mediaUrl,
            duration = message.duration,
            isOwnMessage = isOwnMessage
        )
        isImage -> ImageAttachment(message, onOpenFile, onLongClick)
        isVideo -> VideoAttachment(message, isDownloaded, onOpenFile, onDownloadFile, onLongClick)
        else -> GenericFileAttachment(message, isDownloaded, onOpenFile, onDownloadFile, onSaveFile, onLongClick)
    }
}
