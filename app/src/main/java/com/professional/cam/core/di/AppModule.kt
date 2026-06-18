package com.professional.cam.core.di

import android.content.Context
import com.professional.cam.core.error.ErrorHandler
import com.professional.cam.core.error.ErrorRecoveryManager
import com.professional.cam.core.permission.PermissionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用级别依赖注入模块
 *
 * 提供全局单例依赖：
 * - [ErrorHandler]: 错误处理中心
 * - [ErrorRecoveryManager]: 错误恢复管理器
 * - [PermissionManager]: 权限管理器
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideErrorHandler(): ErrorHandler {
        return ErrorHandler()
    }

    @Provides
    @Singleton
    fun provideErrorRecoveryManager(): ErrorRecoveryManager {
        return ErrorRecoveryManager()
    }

    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): PermissionManager {
        return PermissionManager(context)
    }
}
