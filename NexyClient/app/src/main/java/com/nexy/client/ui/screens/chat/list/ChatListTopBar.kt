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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nexy.client.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListTopBar(
    onOpenDrawer: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    TopAppBar(
        title = {
            val gradientColors = listOf(
                Color(0xFF64B5F6), // Light Blue
                Color(0xFFBA68C8), // Light Purple
                Color(0xFFE57373)  // Light Red
            )
            
            Text(
                text = "Nexy",
                style = TextStyle(
                    brush = Brush.horizontalGradient(colors = gradientColors),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            )
        },
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
