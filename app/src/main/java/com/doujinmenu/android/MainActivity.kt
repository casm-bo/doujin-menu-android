package com.doujinmenu.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doujinmenu.android.ui.DoujinMenuApp
import com.doujinmenu.android.ui.MainViewModel
import com.doujinmenu.android.ui.theme.DoujinMenuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val darkTheme = viewModel.uiState.themeMode.usesDarkTheme(isSystemInDarkTheme())
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                enableEdgeToEdge(systemBarStyle, systemBarStyle)
            }
            DoujinMenuTheme(darkTheme = darkTheme) {
                DoujinMenuApp(viewModel)
            }
        }
    }
}
