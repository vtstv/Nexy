/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.main.components

import androidx.compose.runtime.*

class DialogState {
    var showContacts by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var showCreateGroup by mutableStateOf(false)
    var showSearchGroups by mutableStateOf(false)
    var showGroupSettings by mutableStateOf<Int?>(null)
    
    fun openContacts() { showContacts = true }
    fun closeContacts() { showContacts = false }
    
    fun openSearch() { showSearch = true }
    fun closeSearch() { showSearch = false }
    
    fun openCreateGroup() { showCreateGroup = true }
    fun closeCreateGroup() { showCreateGroup = false }
    
    fun openSearchGroups() { showSearchGroups = true }
    fun closeSearchGroups() { showSearchGroups = false }
    
    fun openGroupSettings(groupId: Int) { showGroupSettings = groupId }
    fun closeGroupSettings() { showGroupSettings = null }
}

@Composable
fun rememberDialogState() = remember { DialogState() }
