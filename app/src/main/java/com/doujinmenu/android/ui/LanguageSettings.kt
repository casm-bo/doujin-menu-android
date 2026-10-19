package com.doujinmenu.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.doujinmenu.android.R

@Composable
internal fun LanguageSettings() {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val options = listOf("" to strings[R.string.language_system], "ko" to "한국어", "en" to "English", "zh" to "中文", "ja" to "日本語")
    Column {
        options.forEach { (tag, label) ->
            Row(Modifier.fillMaxWidth().clickable {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == tag, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label)
            }
        }
    }
}
