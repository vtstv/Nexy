package com.nexy.client.ui.screens.settings

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.biometric.BiometricManager
import com.nexy.client.R
import com.nexy.client.ui.theme.ThemeViewModel
import com.nexy.client.ui.screens.settings.components.*
import com.nexy.client.ui.screens.settings.components.dialogs.CacheSettingsDialog
import com.nexy.client.ui.screens.settings.components.dialogs.PinSetupDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
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
    var showCacheDialog by remember { mutableStateOf(false) }
    var showIncomingColorPicker by remember { mutableStateOf(false) }
    var showOutgoingColorPicker by remember { mutableStateOf(false) }
    
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
            ChatSettingsSection(
                fontScale = fontScale,
                themeStyle = themeStyle,
                incomingTextColor = incomingTextColor,
                outgoingTextColor = outgoingTextColor,
                onFontScaleChange = { themeViewModel.setFontScale(it) },
                onThemeStyleChange = { themeViewModel.setThemeStyle(it) },
                onIncomingColorClick = { showIncomingColorPicker = true },
                onOutgoingColorClick = { showOutgoingColorPicker = true }
            )

            HorizontalDivider()

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

            HorizontalDivider()
            
            VoiceMediaSection(
                voiceMessagesEnabled = voiceMessagesEnabled,
                onVoiceMessagesChange = { viewModel.setVoiceMessagesEnabled(it) }
            )

            HorizontalDivider()

            SecuritySection(
                pinCode = pinCode,
                isBiometricEnabled = isBiometricEnabled,
                canAuthenticate = canAuthenticate,
                onPinToggle = { enabled ->
                    if (enabled) {
                        showPinDialog = true
                    } else {
                        viewModel.setPinCode(null)
                        viewModel.setBiometricEnabled(false)
                    }
                },
                onBiometricToggle = { viewModel.setBiometricEnabled(it) }
            )
            
            HorizontalDivider()
            
            StorageSection(
                cacheSize = cacheSize,
                cacheMaxSize = cacheMaxSize,
                cacheMaxAge = cacheMaxAge,
                onClearCache = { viewModel.clearCache() },
                onCacheSettingsClick = { showCacheDialog = true }
            )
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
}
