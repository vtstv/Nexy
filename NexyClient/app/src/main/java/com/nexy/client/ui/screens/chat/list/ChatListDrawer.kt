/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nexy.client.BuildConfig
import com.nexy.client.R
import com.nexy.client.ServerConfig

@Composable
fun ChatListDrawer(
    userName: String,
    avatarUrl: String?,
    isDarkTheme: Boolean,
    isPinSet: Boolean,
    onNavigateToProfile: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToSearchGroups: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onOpenSavedMessages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLockApp: () -> Unit,
    onShowAboutDialog: () -> Unit,
    onToggleTheme: () -> Unit,
    onShowLogoutDialog: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(260.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.headlineSmall
            )
            
            // User profile section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val model = ServerConfig.getFileUrl(avatarUrl)
                
                if (model != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            HorizontalDivider()
            
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Person, null) },
                label = { Text(stringResource(R.string.my_profile)) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToProfile()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )
            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Group, null) },
                label = { Text(stringResource(R.string.new_group)) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToCreateGroup()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )
            
            /* Search Groups moved to main search
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search Groups") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToSearchGroups()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )
            */
            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.People, null) },
                label = { Text(stringResource(R.string.contacts)) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToContacts()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Bookmark, null) },
                label = { Text(stringResource(R.string.saved_messages)) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSavedMessages()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )

            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, null) },
                label = { Text(stringResource(R.string.settings)) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onNavigateToSettings()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )
            
            if (isPinSet) {
                Spacer(Modifier.height(16.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, null) },
                    label = { Text("Lock App") },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onLockApp()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            HorizontalDivider()
            
            Spacer(Modifier.height(8.dp))

            // About
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("About") },
                selected = false,
                onClick = {
                    onShowAboutDialog()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                label = { Text(if (isDarkTheme) stringResource(R.string.light_theme) else stringResource(R.string.dark_theme)) },
                selected = false,
                onClick = {
                    onToggleTheme()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )
            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Info, null) },
                label = { 
                    Column {
                        Text("Version")
                        Text(
                            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                selected = false,
                onClick = {},
                modifier = Modifier.padding(horizontal = 12.dp).height(56.dp)
            )
            
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.ExitToApp, null) },
                label = { Text(stringResource(R.string.logout)) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onShowLogoutDialog()
                },
                modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
            )
            
            Spacer(Modifier.height(16.dp))
        }
    }
}
