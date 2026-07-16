package com.doujinmenu.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private const val LIGHT_HAPTIC_AMPLITUDE = 51 // 20% of the Android amplitude range (255).
private const val LIGHT_HAPTIC_DURATION_MS = 8L

internal fun Context.performLightHaptic() {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() == true) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(LIGHT_HAPTIC_DURATION_MS, LIGHT_HAPTIC_AMPLITUDE),
        )
    }
}
