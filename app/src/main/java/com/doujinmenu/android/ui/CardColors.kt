package com.doujinmenu.android.ui

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.doujinmenu.android.model.readableForeground

@Composable
internal fun readableContentColor(background: Color): Color = Color(readableForeground(
    MaterialTheme.colorScheme.onSurface.toArgb().toLong() and 0xFFFFFFFF,
    background.toArgb().toLong() and 0xFFFFFFFF,
))

@Composable
internal fun appCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = readableContentColor(MaterialTheme.colorScheme.surfaceContainerLow),
)
