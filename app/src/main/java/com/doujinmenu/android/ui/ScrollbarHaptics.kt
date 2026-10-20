package com.doujinmenu.android.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

internal const val SCROLLBAR_HAPTIC_DURATION_MS = 12L
internal const val SCROLLBAR_HAPTIC_AMPLITUDE = 26
internal val LocalScrollbarHaptic = staticCompositionLocalOf<(() -> Unit)?> { null }

internal fun weakScrollbarHaptic(context: Context, enabled: Boolean, fallback: () -> Unit) {
    if (!enabled || Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
    val vibrator = context.getSystemService(Vibrator::class.java)
    if (vibrator?.hasVibrator() == true && vibrator.hasAmplitudeControl()) {
        vibrator.vibrate(VibrationEffect.createOneShot(SCROLLBAR_HAPTIC_DURATION_MS, SCROLLBAR_HAPTIC_AMPLITUDE),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
    } else fallback()
}
