package com.professional.cam.core.util

import timber.log.Timber

/**
 * 日志工具类
 *
 * 封装 Timber，提供统一的日志接口。
 * 在 debug 模式下自动打印，release 模式下可接入远程日志服务。
 */
object Logger {

    fun v(tag: String, message: String) {
        Timber.tag(tag).v(message)
    }

    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    fun e(tag: String, message: String) {
        Timber.tag(tag).e(message)
    }

    fun e(tag: String, throwable: Throwable, message: String? = null) {
        Timber.tag(tag).e(throwable, message ?: throwable.message ?: "Unknown error")
    }

    // 预定义标签
    object Tag {
        const val CAMERA = "ProCam-Camera"
        const val RECORDING = "ProCam-Recording"
        const val CONTROL = "ProCam-Control"
        const val MONITOR = "ProCam-Monitor"
        const val SYSTEM = "ProCam-System"
        const val UI = "ProCam-UI"
        const val ERROR = "ProCam-Error"
        const val CAPABILITY = "ProCam-Capability"
        const val DI = "ProCam-DI"
    }
}
