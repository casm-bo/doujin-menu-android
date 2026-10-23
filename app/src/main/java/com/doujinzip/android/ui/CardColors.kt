package com.doujinzip.android.ui

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.doujinzip.android.model.readableForeground

@Composable
internal fun readableContentColor(background: Color): Color = Color(readableForeground(
    MaterialTheme.colorScheme.onSurface.toArgb().toLong() and 0xFFFFFFFF,
    background.toArgb().toLong() and 0xFFFFFFFF,
))

@Composable
internal fun appCardColors(selected: Boolean = false): androidx.compose.material3.CardColors {
    val background = if (selected) androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary, .12f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    return CardDefaults.cardColors(containerColor = background, contentColor = readableContentColor(background))
}
