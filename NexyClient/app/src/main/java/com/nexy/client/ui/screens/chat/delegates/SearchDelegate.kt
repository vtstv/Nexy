package com.nexy.client.ui.screens.chat.delegates

import com.nexy.client.data.models.Message
import com.nexy.client.ui.screens.chat.handlers.SearchHandler
import com.nexy.client.ui.screens.chat.state.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class SearchDelegate @Inject constructor(
    private val searchHandler: SearchHandler
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

    fun toggleSearch() {
        uiState.value = uiState.value.copy(
            isSearching = !uiState.value.isSearching,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    fun updateSearchQuery(query: String) {
        uiState.value = uiState.value.copy(searchQuery = query)
        if (query.length > 2) {
            scope.launch {
                searchHandler.searchMessages(getChatId(), query)
                    .onSuccess { results ->
                        uiState.value = uiState.value.copy(searchResults = results)
                    }
            }
        } else {
            uiState.value = uiState.value.copy(searchResults = emptyList())
        }
    }
}
