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
import com.doujinmenu.android.ui.labelResource
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.doujinmenu.android.ui.theme.DoujinMenuTheme

class MainActivity : AppCompatActivity() {
    private var biometricPrompt: androidx.biometric.BiometricPrompt? = null
    private fun attachBiometric(request: com.doujinmenu.android.security.BiometricRequest): androidx.biometric.BiometricPrompt =
        androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { appLock.biometricSuccess(request) }
            override fun onAuthenticationError(code: Int, message: CharSequence) { appLock.biometricError(request, code, message.toString()) }
        }).also { biometricPrompt = it }

    private fun requestBiometric(automatic: Boolean) {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        val available = androidx.biometric.BiometricManager.from(this).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        val request = appLock.beginBiometric(automatic, available) ?: return
        try {
            attachBiometric(request).authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.ui_unlock_app))
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(R.string.ui_use, getString(appLock.method.labelResource))).build())
        } catch (error: RuntimeException) {
            appLock.biometricError(request, androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE, error.message.orEmpty())
        }
    }

    private val appLock: AppLockViewModel by viewModels()

    override fun onStop() {
        if (!isChangingConfigurations) { appLock.onBackground(); biometricPrompt?.cancelAuthentication() }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLock.activeBiometricRequest?.let(::attachBiometric)
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
            DoujinMenuTheme(darkTheme = darkTheme, fontSizeSp = viewModel.uiState.fontSizeSp, style = viewModel.uiState.appStyle) {
                CompositionLocalProvider(LocalGalleryBorders provides viewModel.uiState.galleryBorders, LocalAppLock provides appLock) {
                    val holder = rememberSaveableStateHolder()
                    if (appLock.locked) AppLockScreen(appLock, ::requestBiometric) { moveTaskToBack(true) }
                    else holder.SaveableStateProvider("app") { DoujinMenuApp(viewModel) }
                }
            }
        }
    }
}
