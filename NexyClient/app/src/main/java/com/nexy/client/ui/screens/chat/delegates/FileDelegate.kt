package com.nexy.client.ui.screens.chat.delegates

import android.content.Context
import android.net.Uri
import com.nexy.client.ui.screens.chat.handlers.FileOperationsHandler
import com.nexy.client.ui.screens.chat.state.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class FileDelegate @Inject constructor(
    private val fileOps: FileOperationsHandler
) : ChatViewModelDelegate {

    private lateinit var scope: CoroutineScope
    private lateinit var uiState: MutableStateFlow<ChatUiState>
    private lateinit var getChatId: () -> Int

    override fun initialize(
        scope: CoroutineScope,
        uiState: MutableStateFlow<ChatUiState>,
        getChatId: () -> Int
    ) {
        this.scope = scope
        this.uiState = uiState
        this.getChatId = getChatId
    }

    fun sendFileMessage(context: Context, fileUri: Uri, fileName: String) {
        scope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            uiState.value = uiState.value.copy(isLoading = true)

            fileOps.sendFileMessage(getChatId(), userId, context, fileUri, fileName)
                .onSuccess {
                    uiState.value = uiState.value.copy(error = null, isLoading = false)
                }
                .onFailure { error ->
                    uiState.value = uiState.value.copy(
                        error = error.message ?: "Failed to send file",
                        isLoading = false
                    )
                }
        }
    }

    fun downloadFile(context: Context, fileId: String, fileName: String) {
        scope.launch {
            uiState.value = uiState.value.copy(isLoading = true)

            fileOps.downloadFile(fileId, context, fileName)
                .onSuccess {
                    uiState.value = uiState.value.copy(
                        isLoading = false,
                        error = null,
                        messages = uiState.value.messages
                    )
                }
                .onFailure { error ->
                    uiState.value = uiState.value.copy(
                        error = error.message ?: "Failed to download file",
                        isLoading = false
                    )
                }
        }
    }

    fun openFile(context: Context, fileName: String) {
        fileOps.openFile(context, fileName)?.let { error ->
            uiState.value = uiState.value.copy(error = error)
        }
    }

    fun saveFile(context: Context, fileName: String) {
        scope.launch {
            fileOps.saveFileToDownloads(context, fileName)
                .onSuccess {
                    android.widget.Toast.makeText(
                        context,
                        "File saved to Downloads",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { error ->
                    uiState.value = uiState.value.copy(
                        error = error.message ?: "Failed to save file"
                    )
                }
        }
    }
    
    fun sendVoiceMessage(audioFile: java.io.File, durationMs: Long) {
        scope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            uiState.value = uiState.value.copy(isLoading = true)

            fileOps.sendVoiceMessage(getChatId(), userId, audioFile, durationMs)
                .onSuccess {
                    uiState.value = uiState.value.copy(error = null, isLoading = false)
                }
                .onFailure { error ->
                    uiState.value = uiState.value.copy(
                        error = error.message ?: "Failed to send voice message",
                        isLoading = false
                    )
                }
        }
    }
}
