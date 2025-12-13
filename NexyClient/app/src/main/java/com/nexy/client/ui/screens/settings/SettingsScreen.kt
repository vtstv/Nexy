package com.nexy.client.ui.screens.settings

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.biometric.BiometricManager
import com.nexy.client.R
import com.nexy.client.ui.theme.ThemeViewModel
import com.nexy.client.ui.screens.settings.components.*
import com.nexy.client.ui.screens.settings.components.dialogs.CacheSettingsDialog
import com.nexy.client.ui.screens.settings.components.dialogs.PinSetupDialog

private enum class SettingsCategory {
    MAIN,
    APPEARANCE,
    CHAT,
    NOTIFICATIONS,
    PRIVACY,
    SECURITY,
    STORAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    var currentCategory by remember { mutableStateOf(SettingsCategory.MAIN) }
    
    val pinCode by viewModel.pinCode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val cacheMaxSize by viewModel.cacheMaxSize.collectAsState()
    val cacheMaxAge by viewModel.cacheMaxAge.collectAsState()
    
    val themeStyle by themeViewModel.themeStyle.collectAsState()
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsState()
    val fontScale by themeViewModel.fontScale.collectAsState()
    val uiScale by themeViewModel.uiScale.collectAsState()
    val incomingTextColor by themeViewModel.incomingTextColor.collectAsState()
    val outgoingTextColor by themeViewModel.outgoingTextColor.collectAsState()
    val avatarSize by themeViewModel.avatarSize.collectAsState()
    
    val pushNotificationsEnabled by viewModel.pushNotificationsEnabled.collectAsState()
    val notificationSoundEnabled by viewModel.notificationSoundEnabled.collectAsState()
    val notificationSoundUri by viewModel.notificationSoundUri.collectAsState()
    val notificationVibrationEnabled by viewModel.notificationVibrationEnabled.collectAsState()
    val voiceMessagesEnabled by viewModel.voiceMessagesEnabled.collectAsState()
    val isBackgroundServiceEnabled by viewModel.isBackgroundServiceEnabled.collectAsState()
    val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsState()
    val typingIndicatorsEnabled by viewModel.typingIndicatorsEnabled.collectAsState()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val phonePrivacy by viewModel.phonePrivacy.collectAsState()
    val allowPhoneDiscovery by viewModel.allowPhoneDiscovery.collectAsState()
    val showNotepad by viewModel.showNotepad.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val isLoadingSessions by viewModel.isLoadingSessions.collectAsState()
    
    var showPinDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showIncomingColorPicker by remember { mutableStateOf(false) }
    var showOutgoingColorPicker by remember { mutableStateOf(false) }
    var showPhonePrivacyDialog by remember { mutableStateOf(false) }
    var editingPhoneNumber by remember(phoneNumber) { mutableStateOf(phoneNumber) }
    
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setNotificationSoundUri(uri?.toString())
        }
    }
    
    val canAuthenticate = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    BackHandler(enabled = currentCategory != SettingsCategory.MAIN) {
        currentCategory = SettingsCategory.MAIN
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when (currentCategory) {
                            SettingsCategory.MAIN -> stringResource(R.string.settings)
                            SettingsCategory.APPEARANCE -> stringResource(R.string.appearance)
                            SettingsCategory.CHAT -> "Chat Settings"
                            SettingsCategory.NOTIFICATIONS -> "Notifications"
                            SettingsCategory.PRIVACY -> "Privacy"
                            SettingsCategory.SECURITY -> "Security"
                            SettingsCategory.STORAGE -> "Storage & Data"
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentCategory == SettingsCategory.MAIN) {
                            onNavigateBack()
                        } else {
                            currentCategory = SettingsCategory.MAIN
                        }
                    }) {
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
            when (currentCategory) {
                SettingsCategory.MAIN -> {
                    SettingsCategoryItem(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.appearance),
                        onClick = { currentCategory = SettingsCategory.APPEARANCE }
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Email,
                        title = "Chat Settings",
                        onClick = { currentCategory = SettingsCategory.CHAT }
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        onClick = { currentCategory = SettingsCategory.NOTIFICATIONS }
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.VisibilityOff,
                        title = "Privacy",
                        onClick = { currentCategory = SettingsCategory.PRIVACY }
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Security,
                        title = "Security",
                        onClick = { currentCategory = SettingsCategory.SECURITY }
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Info,
                        title = "Storage & Data",
                        onClick = { currentCategory = SettingsCategory.STORAGE }
                    )
                }
                SettingsCategory.APPEARANCE -> {
                    AppearanceSection(
                        isDarkTheme = isDarkTheme,
                        themeStyle = themeStyle,
                        uiScale = uiScale,
                        onToggleTheme = { themeViewModel.toggleTheme() },
                        onThemeStyleChange = { themeViewModel.setThemeStyle(it) },
                        onUiScaleChange = { themeViewModel.setUiScale(it) }
                    )
                }
                SettingsCategory.CHAT -> {
                    ChatSettingsSection(
                        fontScale = fontScale,
                        themeStyle = themeStyle,
                        incomingTextColor = incomingTextColor,
                        outgoingTextColor = outgoingTextColor,
                        avatarSize = avatarSize,
                        showNotepad = showNotepad,
                        onFontScaleChange = { themeViewModel.setFontScale(it) },
                        onThemeStyleChange = { themeViewModel.setThemeStyle(it) },
                        onIncomingColorClick = { showIncomingColorPicker = true },
                        onOutgoingColorClick = { showOutgoingColorPicker = true },
                        onAvatarSizeChange = { themeViewModel.setAvatarSize(it) },
                        onShowNotepadChange = { viewModel.setShowNotepad(it) }
                    )
                    HorizontalDivider()
                    VoiceMediaSection(
                        voiceMessagesEnabled = voiceMessagesEnabled,
                        onVoiceMessagesChange = { viewModel.setVoiceMessagesEnabled(it) }
                    )
                }
                SettingsCategory.NOTIFICATIONS -> {
                    NotificationsSection(
                        pushNotificationsEnabled = pushNotificationsEnabled,
                        notificationSoundEnabled = notificationSoundEnabled,
                        notificationSoundUri = notificationSoundUri,
                        notificationVibrationEnabled = notificationVibrationEnabled,
                        isBackgroundServiceEnabled = isBackgroundServiceEnabled,
                        onPushNotificationsChange = { viewModel.setPushNotificationsEnabled(it) },
                        onNotificationSoundChange = { viewModel.setNotificationSoundEnabled(it) },
                        onNotificationVibrationChange = { viewModel.setNotificationVibrationEnabled(it) },
                        onBackgroundServiceChange = { viewModel.setBackgroundServiceEnabled(it) },
                        ringtoneLauncher = ringtoneLauncher
                    )
                }
                SettingsCategory.SECURITY -> {
                    SecuritySection(
                        pinCode = pinCode,
                        isBiometricEnabled = isBiometricEnabled,
                        canAuthenticate = canAuthenticate,
                        sessions = sessions,
                        isLoadingSessions = isLoadingSessions,
                        onPinToggle = { enabled ->
                            if (enabled) {
                                showPinDialog = true
                            } else {
                                viewModel.setPinCode(null)
                                viewModel.setBiometricEnabled(false)
                            }
                        },
                        onBiometricToggle = { viewModel.setBiometricEnabled(it) },
                        onLoadSessions = { viewModel.loadSessions() },
                        onLogoutSession = { sessionId -> viewModel.logoutSession(sessionId) },
                        onLogoutAllOtherSessions = { viewModel.logoutAllOtherSessions() },
                        onUpdateSessionSettings = { sessionId, acceptSecretChats, acceptCalls ->
                            viewModel.updateSessionSettings(sessionId, acceptSecretChats, acceptCalls)
                        }
                    )
                }
                SettingsCategory.PRIVACY -> {
                    // Phone Number Section
                    ListItem(
                        headlineContent = { Text("Phone Number") },
                        supportingContent = { 
                            Text(if (phoneNumber.isNotEmpty()) phoneNumber else "Not set") 
                        },
                        modifier = Modifier.clickable { /* Could add edit dialog */ }
                    )
                    HorizontalDivider()
                    
                    OutlinedTextField(
                        value = editingPhoneNumber,
                        onValueChange = { editingPhoneNumber = it },
                        label = { Text("Phone Number") },
                        supportingText = { Text("Format: +1234567890") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (editingPhoneNumber != phoneNumber) {
                                TextButton(onClick = { 
                                    viewModel.setPhoneNumber(editingPhoneNumber)
                                }) {
                                    Text("Save")
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    
                    ListItem(
                        headlineContent = { Text("Phone Discovery") },
                        supportingContent = { Text("Allow others to find you by phone number") },
                        trailingContent = {
                            Switch(
                                checked = allowPhoneDiscovery,
                                onCheckedChange = { viewModel.setAllowPhoneDiscovery(it) }
                            )
                        }
                    )
                    HorizontalDivider()
                    
                    ListItem(
                        headlineContent = { Text("Who can see my phone") },
                        supportingContent = { 
                            Text(when (phonePrivacy) {
                                "everyone" -> "Everyone"
                                "contacts" -> "My contacts"
                                "nobody" -> "Nobody"
                                else -> "My contacts"
                            })
                        },
                        modifier = Modifier.clickable { showPhonePrivacyDialog = true }
                    )
                    HorizontalDivider()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Activity Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    ListItem(
                        headlineContent = { Text("Read Receipts") },
                        supportingContent = { Text("Show when you read messages") },
                        trailingContent = {
                            Switch(
                                checked = readReceiptsEnabled,
                                onCheckedChange = { viewModel.setReadReceiptsEnabled(it) }
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Typing Indicators") },
                        supportingContent = { Text("Show when you are typing") },
                        trailingContent = {
                            Switch(
                                checked = typingIndicatorsEnabled,
                                onCheckedChange = { viewModel.setTypingIndicatorsEnabled(it) }
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Online Status") },
                        supportingContent = { Text("Show your online status. When disabled, you won't see others' status either.") },
                        trailingContent = {
                            Switch(
                                checked = showOnlineStatus,
                                onCheckedChange = { viewModel.setShowOnlineStatus(it) }
                            )
                        }
                    )
                }
                SettingsCategory.STORAGE -> {
                    StorageSection(
                        cacheSize = cacheSize,
                        cacheMaxSize = cacheMaxSize,
                        cacheMaxAge = cacheMaxAge,
                        onClearCache = { viewModel.clearCache() },
                        onCacheSettingsClick = { showCacheDialog = true }
                    )
                }
            }
        }
    }
    
    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onPinSet = { pin ->
                viewModel.setPinCode(pin)
                showPinDialog = false
            }
        )
    }
    
    if (showCacheDialog) {
        CacheSettingsDialog(
            currentMaxSize = cacheMaxSize,
            currentMaxAge = cacheMaxAge,
            onDismiss = { showCacheDialog = false },
            onMaxSizeChange = { viewModel.setCacheMaxSize(it) },
            onMaxAgeChange = { viewModel.setCacheMaxAge(it) }
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

    if (showPhonePrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPhonePrivacyDialog = false },
            title = { Text("Who can see my phone") },
            text = {
                Column {
                    listOf(
                        "everyone" to "Everyone",
                        "contacts" to "My contacts",
                        "nobody" to "Nobody"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPhonePrivacy(value)
                                    showPhonePrivacyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = phonePrivacy == value,
                                onClick = {
                                    viewModel.setPhonePrivacy(value)
                                    showPhonePrivacyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhonePrivacyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

