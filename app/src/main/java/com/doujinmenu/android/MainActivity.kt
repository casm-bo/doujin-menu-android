package com.doujinmenu.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.doujinmenu.android.ui.LocalGalleryBorders
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doujinmenu.android.ui.DoujinMenuApp
import com.doujinmenu.android.ui.MainViewModel
import com.doujinmenu.android.ui.AppLockViewModel
import com.doujinmenu.android.ui.AppLockScreen
import com.doujinmenu.android.ui.LocalAppLock
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.doujinmenu.android.ui.theme.DoujinMenuTheme

class MainActivity : AppCompatActivity() {
    private val appLock: AppLockViewModel by viewModels()

    override fun onStop() {
        if (!isChangingConfigurations) appLock.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val darkTheme = viewModel.uiState.themeMode.usesDarkTheme(isSystemInDarkTheme())
            SideEffect {
                if (appLock.enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val transparent = android.graphics.Color.TRANSPARENT
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                enableEdgeToEdge(systemBarStyle, systemBarStyle)
            }
            DoujinMenuTheme(darkTheme = darkTheme, fontSizeSp = viewModel.uiState.fontSizeSp) {
                CompositionLocalProvider(LocalGalleryBorders provides viewModel.uiState.galleryBorders, LocalAppLock provides appLock) {
                    val holder = rememberSaveableStateHolder()
                    if (appLock.locked) AppLockScreen(appLock) { moveTaskToBack(true) }
                    else holder.SaveableStateProvider("app") { DoujinMenuApp(viewModel) }
                }
            }
        }
    }
}
