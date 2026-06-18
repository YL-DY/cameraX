package com.professional.cam.core.extension

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager

/**
 * Context 扩展函数
 */

fun Context.getVibrator(): Vibrator {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}

fun Context.vibrateShort() {
    val vibrator = getVibrator()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}

fun Context.isScreenOn(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
        powerManager.isInteractive
    } else {
        @Suppress("DEPRECATION")
        powerManager.isScreenOn
    }
}

val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager
