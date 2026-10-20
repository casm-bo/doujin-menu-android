package com.doujinmenu.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.R
import com.doujinmenu.android.model.*

@Composable
internal fun StyleSettings(style: AppStyle, onChange: (AppStyle) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var dark by rememberSaveable { mutableStateOf(false) }
    val palette = style.palette(dark)
    Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings[R.string.ui_style], style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppStylePreset.entries.forEach { preset ->
                    FilterChip(selected = style.preset == preset, onClick = { onChange(AppStyle(preset)) },
                        label = { Text(when (preset) {
                            AppStylePreset.DEFAULT -> strings[R.string.ui_default_style]
                            AppStylePreset.OCEAN -> "Ocean Breeze"
                            AppStylePreset.AMBER -> "Amber Minimal"
                            AppStylePreset.CATPPUCCIN -> "Catppuccin"
                        }) })
                }
            }
            HorizontalDivider()
            Text(strings[R.string.ui_preview], style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !dark, onClick = { dark = false }, label = { Text(strings[R.string.ui_light]) })
                FilterChip(selected = dark, onClick = { dark = true }, label = { Text(strings[R.string.ui_dark]) })
            }
            Surface(color = Color(palette.background), contentColor = Color(readableForeground(palette.text, palette.background))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Color(palette.toolbar), contentColor = Color(readableForeground(palette.text, palette.toolbar))) {
                        Text(strings[R.string.ui_toolbar_color], Modifier.fillMaxWidth().padding(12.dp))
                    }
                    Surface(color = Color(palette.card), contentColor = Color(readableForeground(palette.text, palette.card))) {
                        Text(strings[R.string.ui_preview], Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
            }
        }
    }
}
