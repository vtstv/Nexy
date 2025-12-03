/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.main.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ScreenConfig(
    val isTablet: Boolean,
    val isLandscape: Boolean,
    val useSplitScreen: Boolean,
    val screenWidth: Dp,
    val dialogWidthFraction: Float,
    val dialogPadding: Dp
)

@Composable
fun rememberScreenConfig(): ScreenConfig {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = screenWidth >= 600.dp
    val useSplitScreen = isTablet && isLandscape
    
    return ScreenConfig(
        isTablet = isTablet,
        isLandscape = isLandscape,
        useSplitScreen = useSplitScreen,
        screenWidth = screenWidth,
        dialogWidthFraction = if (useSplitScreen) 0.375f else 1.0f,
        dialogPadding = if (useSplitScreen) 16.dp else 0.dp
    )
}
