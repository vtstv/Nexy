/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexy.client.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListTopBar(
    onOpenDrawer: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.chats)) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, "Menu")
            }
        },
        actions = {
            IconButton(onClick = onNavigateToContacts) {
                Icon(Icons.Default.People, stringResource(R.string.contacts))
            }
            IconButton(onClick = onNavigateToProfile) {
                Icon(Icons.Default.Person, stringResource(R.string.my_profile))
            }
        }
    )
}
