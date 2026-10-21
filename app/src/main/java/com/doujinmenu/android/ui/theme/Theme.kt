package com.doujinmenu.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.doujinmenu.android.model.readableAccent
import com.doujinmenu.android.model.AppStyle
import com.doujinmenu.android.model.readableForeground

@Composable
fun DoujinMenuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: AppStyle = AppStyle(),
    fontSizeSp: Float = com.doujinmenu.android.model.DEFAULT_FONT_SIZE_SP,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, style) {
    val palette = style.palette(darkTheme)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    fun foreground(background: Long) = Color(readableForeground(palette.text, background))
    val accent = Color(palette.accent)
    val tool = Color(palette.toolbar)
    val card = Color(palette.card)
    val primary = readableAccent(palette.accent, palette.card)
    val primaryContainer = lerp(tool, accent, .22f)
    val secondaryContainer = lerp(tool, accent, .14f)
    base.copy(
        primary = Color(primary),
        onPrimary = foreground(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = foreground(primaryContainer.toArgb().toLong() and 0xFFFFFFFF),
        secondary = Color(readableAccent(palette.accent, palette.toolbar)),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = foreground(secondaryContainer.toArgb().toLong() and 0xFFFFFFFF),
        background = Color(palette.background), onBackground = foreground(palette.background),
        surface = tool, onSurface = foreground(palette.toolbar), onSurfaceVariant = foreground(palette.card),
        surfaceVariant = lerp(tool, accent, .08f), surfaceContainer = tool,
        surfaceContainerLow = card, surfaceContainerHigh = card, surfaceContainerHighest = card,
        surfaceContainerLowest = Color(palette.background), surfaceBright = card,
    )
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontSizeSp / 16f)) {
    MaterialTheme(
        colorScheme = colors,
        content = { CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content) },
    )
    }
}
