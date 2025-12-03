package com.nexy.client.data.repository.file

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.models.Message
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.data.models.MessageType
import com.nexy.client.data.repository.message.MessageMappers
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

import android.webkit.MimeTypeMap
import android.provider.OpenableColumns

@Singleton
class FileOperations @Inject constructor(
    private val apiService: NexyApiService,
    private val messageDao: MessageDao,
    private val webSocketClient: NexyWebSocketClient,
    private val messageMappers: MessageMappers
) {
    
    companion object {
        private const val TAG = "FileOperations"
        private const val MAX_FILE_SIZE = 10485760L // 10MB in bytes
        
        private val ALLOWED_MIME_TYPES = listOf(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm",
            "audio/mpeg", "audio/wav", "audio/ogg",
            "application/pdf", "application/zip", "application/x-zip-compressed",
            "application/json", "text/plain",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream"
        )
    }
    
    suspend fun sendFileMessage(
        chatId: Int,
        senderId: Int,
        context: Context,
        fileUri: Uri,
        fileName: String
    ): Result<Message> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending file message: chatId=$chatId, fileName=$fileName")
                
                // Get MIME type first to validate
                val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
                Log.d(TAG, "File MIME type: $mimeType")
                
                // Validate MIME type against allowed types
                if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
                    Log.e(TAG, "File type not allowed: $mimeType")
                    return@withContext Result.failure(
                        Exception("File type '$mimeType' is not supported. Allowed types: images, videos, audio, PDF, ZIP, JSON, TXT, DOC, XLS")
                    )
                }
                
                // Create temp file from URI
                val inputStream = context.contentResolver.openInputStream(fileUri)
                    ?: return@withContext Result.failure(Exception("Cannot open file"))
                
                val tempFile = File(context.cacheDir, fileName)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                // Check file size (10MB limit)
                if (tempFile.length() > MAX_FILE_SIZE) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("File size exceeds 10MB limit"))
                }
                
                // Create multipart request
                val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val multipartBody = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val typeBody = "file".toRequestBody("text/plain".toMediaTypeOrNull())
                
                // Upload file to server
                Log.d(TAG, "Uploading file to server...")
                val uploadResponse = apiService.uploadFile(multipartBody, typeBody)
                
                // Clean up temp file
                tempFile.delete()
                
                if (!uploadResponse.isSuccessful) {
                    val errorBody = uploadResponse.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Upload failed: ${uploadResponse.code()} - $errorBody")
                    return@withContext Result.failure(Exception("Upload failed: $errorBody"))
                }
                
                if (uploadResponse.body() == null) {
                    return@withContext Result.failure(Exception("Upload failed: empty response"))
                }
                
                val fileUrl = uploadResponse.body()!!.url
                Log.d(TAG, "File uploaded successfully: $fileUrl")
                
                // Create message with file URL
                val messageId = generateMessageId()
                val message = Message(
                    id = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    content = fileName,
                    type = MessageType.FILE,
                    status = MessageStatus.SENDING,
                    mediaUrl = fileUrl,
                    mediaType = mimeType
                )
                
                // Insert to local DB
                messageDao.insertMessage(messageMappers.modelToEntity(message))
                
                // Send via WebSocket (assuming WebSocket supports file messages)
                // For now, we'll just send a text notification
                webSocketClient.sendTextMessage(chatId, senderId, "📎 $fileName", messageId)
                
                Result.success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file message", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun downloadFile(fileId: String, context: Context, fileName: String): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading file: fileId=$fileId, fileName=$fileName")
                
                val response = apiService.downloadFile(fileId)
                
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Download failed: ${response.code()} - $errorBody")
                    return@withContext Result.failure(Exception("Download failed: $errorBody"))
                }
                
                val body = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
                
                val outputFile = File(context.getExternalFilesDir(null), fileName)
                FileOutputStream(outputFile).use { output ->
                    body.byteStream().copyTo(output)
                }
                
                Log.d(TAG, "File downloaded successfully to: ${outputFile.absolutePath}")
                Result.success(Uri.fromFile(outputFile))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download file", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun uploadFile(context: Context, fileUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(context, fileUri) ?: "upload_${System.currentTimeMillis()}"
                Log.d(TAG, "Uploading file: fileName=$fileName")
                
                // Get MIME type
                val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
                
                // Create temp file from URI
                val inputStream = context.contentResolver.openInputStream(fileUri)
                    ?: return@withContext Result.failure(Exception("Cannot open file"))
                
                val tempFile = File(context.cacheDir, fileName)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                // Check file size (10MB limit)
                if (tempFile.length() > MAX_FILE_SIZE) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("File size exceeds 10MB limit"))
                }
                
                // Create multipart request
                val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val multipartBody = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val typeBody = "image".toRequestBody("text/plain".toMediaTypeOrNull())
                
                // Upload file to server
                val uploadResponse = apiService.uploadFile(multipartBody, typeBody)
                
                // Clean up temp file
                tempFile.delete()
                
                if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                    val fileUrl = uploadResponse.body()!!.url
                    Result.success(fileUrl)
                } else {
                    Result.failure(Exception("Upload failed: ${uploadResponse.message()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                Result.failure(e)
            }
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get file name", e)
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun compressImage(file: File, maxSize: Long): File? {
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
            var quality = 90
            val stream = java.io.ByteArrayOutputStream()
            
            do {
                stream.reset()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                quality -= 10
            } while (stream.size() > maxSize && quality > 10)
            
            if (stream.size() > maxSize) return null
            
            val compressedFile = File(file.parent, "compressed_" + file.name)
            java.io.FileOutputStream(compressedFile).use { out ->
                out.write(stream.toByteArray())
            }
            return compressedFile
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun generateMessageId(): String {
        return "${System.currentTimeMillis()}-${(0..999999).random()}"
    }
}
