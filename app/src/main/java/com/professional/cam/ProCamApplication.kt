package com.professional.cam

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * ProCam Application 入口
 *
 * 职责：
 * 1. Hilt 依赖注入初始化
 * 2. Timber 日志系统初始化
 * 3. 全局未捕获异常处理
 */
@HiltAndroidApp
class ProCamApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeLogging()
        initializeUncaughtExceptionHandler()
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Release 模式下可接入 Firebase Crashlytics 或其他日志服务
    }

    private fun initializeUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Uncaught exception in thread: ${thread.name}")
            // 在此可添加崩溃日志上报逻辑
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
