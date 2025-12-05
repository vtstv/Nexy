package com.nexy.client.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexy.client.R

@Composable
fun ConnectionStatusBanner(
    isConnected: Boolean,
    pendingMessageCount: Int,
    modifier: Modifier = Modifier
) {
    val showBanner = !isConnected || pendingMessageCount > 0
    
    AnimatedVisibility(
        visible = showBanner,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (!isConnected) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (!isConnected) Icons.Outlined.CloudOff else Icons.Outlined.Sync,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (!isConnected) MaterialTheme.colorScheme.onErrorContainer
                       else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !isConnected -> stringResource(R.string.waiting_for_network)
                    pendingMessageCount > 0 -> stringResource(R.string.sending_pending_messages, pendingMessageCount)
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (!isConnected) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
