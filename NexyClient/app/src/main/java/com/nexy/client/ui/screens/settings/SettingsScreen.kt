package com.nexy.client.ui.screens.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexy.client.R
import com.nexy.client.ui.theme.ThemeViewModel
import com.nexy.client.ui.theme.ThemeStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val pinCode by viewModel.pinCode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val cacheMaxSize by viewModel.cacheMaxSize.collectAsState()
    val cacheMaxAge by viewModel.cacheMaxAge.collectAsState()
    
    val themeStyle by themeViewModel.themeStyle.collectAsState()
    val fontScale by themeViewModel.fontScale.collectAsState()
    val incomingTextColor by themeViewModel.incomingTextColor.collectAsState()
    val outgoingTextColor by themeViewModel.outgoingTextColor.collectAsState()
    
    val pushNotificationsEnabled by viewModel.pushNotificationsEnabled.collectAsState()
    val notificationSoundEnabled by viewModel.notificationSoundEnabled.collectAsState()
    val notificationSoundUri by viewModel.notificationSoundUri.collectAsState()
    val notificationVibrationEnabled by viewModel.notificationVibrationEnabled.collectAsState()
    val voiceMessagesEnabled by viewModel.voiceMessagesEnabled.collectAsState()
    val isBackgroundServiceEnabled by viewModel.isBackgroundServiceEnabled.collectAsState()
    
    var showPinDialog by remember { mutableStateOf(false) }
    
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setNotificationSoundUri(uri?.toString())
        }
    }
    var showCacheDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var showIncomingColorPicker by remember { mutableStateOf(false) }
    var showOutgoingColorPicker by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    val canAuthenticate = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Chat Settings Section
            Text(
                text = "Chat Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Font Size") },
                supportingContent = { 
                    Column {
                        Slider(
                            value = fontScale,
                            onValueChange = { themeViewModel.setFontScale(it) },
                            valueRange = 0.7f..1.6f,
                            steps = 8
                        )
                        Text(
                            text = when {
                                fontScale <= 0.8f -> "Tiny"
                                fontScale <= 0.95f -> "Small"
                                fontScale <= 1.15f -> "Medium"
                                fontScale <= 1.35f -> "Large"
                                fontScale <= 1.5f -> "Extra Large"
                                else -> "Huge"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Chat Color Theme") },
                supportingContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ThemeStyle.values().forEach { style ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(getThemeColor(style))
                                    .clickable { themeViewModel.setThemeStyle(style) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (themeStyle == style) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Incoming Message Text Color") },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (incomingTextColor != 0L) Color(incomingTextColor) else MaterialTheme.colorScheme.onSurface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showIncomingColorPicker = true }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Outgoing Message Text Color") },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (outgoingTextColor != 0L) Color(outgoingTextColor) else MaterialTheme.colorScheme.onPrimaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showOutgoingColorPicker = true }
                    )
                }
            )

            HorizontalDivider()

            // Notifications Section
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Push Notifications") },
                supportingContent = { Text("Receive notifications for new messages") },
                trailingContent = {
                    Switch(
                        checked = pushNotificationsEnabled,
                        onCheckedChange = { viewModel.setPushNotificationsEnabled(it) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Sound") },
                supportingContent = { Text("Play sound for incoming messages") },
                trailingContent = {
                    Switch(
                        checked = notificationSoundEnabled,
                        onCheckedChange = { viewModel.setNotificationSoundEnabled(it) },
                        enabled = pushNotificationsEnabled
                    )
                }
            )
            
            if (notificationSoundEnabled && pushNotificationsEnabled) {
                val currentSoundName = remember(notificationSoundUri) {
                    try {
                        if (notificationSoundUri == null) {
                            "Default"
                        } else {
                            RingtoneManager.getRingtone(context, Uri.parse(notificationSoundUri))
                                .getTitle(context)
                        }
                    } catch (e: Exception) {
                        "Unknown"
                    }
                }
                
                ListItem(
                    headlineContent = { Text("Notification Sound") },
                    supportingContent = { Text(currentSoundName) },
                    modifier = Modifier.clickable {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            
                            val existingUri = if (notificationSoundUri != null) {
                                Uri.parse(notificationSoundUri)
                            } else {
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            }
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                        }
                        ringtoneLauncher.launch(intent)
                    }
                )
            }

            ListItem(
                headlineContent = { Text("Vibration") },
                supportingContent = { Text("Vibrate for incoming messages") },
                trailingContent = {
                    Switch(
                        checked = notificationVibrationEnabled,
                        onCheckedChange = { viewModel.setNotificationVibrationEnabled(it) },
                        enabled = pushNotificationsEnabled
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Background Service") },
                supportingContent = { Text("Keep connection alive in background (uses more battery)") },
                trailingContent = {
                    Switch(
                        checked = isBackgroundServiceEnabled,
                        onCheckedChange = { viewModel.setBackgroundServiceEnabled(it) }
                    )
                }
            )

            HorizontalDivider()
            
            // Voice & Media Section
            Text(
                text = "Voice & Media",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("Voice Messages") },
                supportingContent = { Text("Enable voice message recording and playback") },
                trailingContent = {
                    Switch(
                        checked = voiceMessagesEnabled,
                        onCheckedChange = { viewModel.setVoiceMessagesEnabled(it) }
                    )
                }
            )

            HorizontalDivider()

            // Security Section
            Text(
                text = "Security",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("App PIN Code") },
                supportingContent = { Text(if (pinCode != null) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(
                        checked = pinCode != null,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinDialog = true
                            } else {
                                viewModel.setPinCode(null)
                                viewModel.setBiometricEnabled(false)
                            }
                        }
                    )
                }
            )
            
            if (pinCode != null && canAuthenticate) {
                ListItem(
                    headlineContent = { Text("Biometric Unlock") },
                    supportingContent = { Text("Use fingerprint or face unlock") },
                    trailingContent = {
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
                        )
                    }
                )
            }
            
            HorizontalDivider()
            
            // Storage Section
            Text(
                text = "Storage & Cache",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("Current Cache Size") },
                supportingContent = { Text(formatFileSize(cacheSize)) },
                trailingContent = {
                    Button(onClick = { viewModel.clearCache() }) {
                        Text("Clear")
                    }
                }
            )
            
            ListItem(
                headlineContent = { Text("Max Cache Size") },
                supportingContent = { Text(formatFileSize(cacheMaxSize)) },
                modifier = Modifier.clickable { showCacheDialog = true }
            )
            
            ListItem(
                headlineContent = { Text("Auto-delete older than") },
                supportingContent = { Text(formatDuration(cacheMaxAge)) },
                modifier = Modifier.clickable { showCacheDialog = true }
            )
        }
    }
    
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set PIN Code") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("Enter 4-digit PIN") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPin.length == 4) {
                            viewModel.setPinCode(newPin)
                            showPinDialog = false
                            newPin = ""
                        }
                    }
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("Cache Settings") },
            text = {
                Column {
                    Text("Max Cache Size")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = cacheMaxSize == 1024L * 1024L * 50L,
                            onClick = { viewModel.setCacheMaxSize(1024L * 1024L * 50L) }
                        )
                        Text("50 MB")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = cacheMaxSize == 1024L * 1024L * 100L,
                            onClick = { viewModel.setCacheMaxSize(1024L * 1024L * 100L) }
                        )
                        Text("100 MB")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = cacheMaxSize == 1024L * 1024L * 500L,
                            onClick = { viewModel.setCacheMaxSize(1024L * 1024L * 500L) }
                        )
                        Text("500 MB")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text("Auto-delete older than")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = cacheMaxAge == 1000L * 60L * 60L * 24L * 3L,
                            onClick = { viewModel.setCacheMaxAge(1000L * 60L * 60L * 24L * 3L) }
                        )
                        Text("3 Days")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = cacheMaxAge == 1000L * 60L * 60L * 24L * 7L,
                            onClick = { viewModel.setCacheMaxAge(1000L * 60L * 60L * 24L * 7L) }
                        )
                        Text("1 Week")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = cacheMaxAge == 1000L * 60L * 60L * 24L * 30L,
                            onClick = { viewModel.setCacheMaxAge(1000L * 60L * 60L * 24L * 30L) }
                        )
                        Text("1 Month")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCacheDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
    
    if (showIncomingColorPicker) {
        ColorPickerDialog(
            initialColor = if (incomingTextColor != 0L) Color(incomingTextColor) else MaterialTheme.colorScheme.onSurface,
            onDismiss = { showIncomingColorPicker = false },
            onColorSelected = { color ->
                themeViewModel.setIncomingTextColor(color.toArgb().toLong())
                showIncomingColorPicker = false
            }
        )
    }

    if (showOutgoingColorPicker) {
        ColorPickerDialog(
            initialColor = if (outgoingTextColor != 0L) Color(outgoingTextColor) else MaterialTheme.colorScheme.onPrimaryContainer,
            onDismiss = { showOutgoingColorPicker = false },
            onColorSelected = { color ->
                themeViewModel.setOutgoingTextColor(color.toArgb().toLong())
                showOutgoingColorPicker = false
            }
        )
    }
}

fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$size B"
    }
}

fun formatDuration(millis: Long): String {
    val days = millis / (1000 * 60 * 60 * 24)
    return "$days Days"
}

private fun getThemeColor(style: ThemeStyle): Color {
    return when (style) {
        ThemeStyle.Pink -> Color(0xFFFFB7D5)
        ThemeStyle.Blue -> Color(0xFFB3C5FF)
        ThemeStyle.Green -> Color(0xFFB7F397)
        ThemeStyle.Purple -> Color(0xFFD0BCFF)
        ThemeStyle.Orange -> Color(0xFFFFB784)
        ThemeStyle.Teal -> Color(0xFF80D5D4)
    }
}
