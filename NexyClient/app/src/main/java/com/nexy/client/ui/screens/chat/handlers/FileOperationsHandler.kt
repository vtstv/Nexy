package com.nexy.client.ui.screens.chat.handlers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nexy.client.data.models.Message
import com.nexy.client.data.repository.ChatRepository
import java.io.File
import javax.inject.Inject

class FileOperationsHandler @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend fun sendFileMessage(
        chatId: Int,
        userId: Int,
        context: Context,
        fileUri: Uri,
        fileName: String
    ): Result<Message> {
        return chatRepository.sendFileMessage(chatId, userId, context, fileUri, fileName)
    }
    
    suspend fun sendVoiceMessage(
        chatId: Int,
        userId: Int,
        audioFile: File,
        durationMs: Long
    ): Result<Message> {
        return chatRepository.sendVoiceMessage(chatId, userId, audioFile, durationMs)
    }

    suspend fun downloadFile(
        fileId: String,
        context: Context,
        fileName: String
    ): Result<Uri> {
        return chatRepository.downloadFile(fileId, context, fileName)
    }

    suspend fun saveFileToDownloads(
        context: Context,
        fileName: String
    ): Result<String> {
        return chatRepository.saveFileToDownloads(context, fileName)
    }

    fun openFile(context: Context, fileName: String): String? {
        try {
            val file = File(context.getExternalFilesDir(null), fileName)
            if (!file.exists()) {
                return "File not found"
            }
            
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, context.contentResolver.getType(contentUri) ?: "*/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            try {
                context.startActivity(Intent.createChooser(intent, "Open file"))
                return null
            } catch (e: ActivityNotFoundException) {
                return "No app found to open this file"
            }
        } catch (e: Exception) {
            android.util.Log.e("FileOperationsHandler", "Error opening file", e)
            return "Error opening file: ${e.message}"
        }
    }
}
