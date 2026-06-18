package com.professional.cam.core.di

import android.content.Context
import com.professional.cam.camera.capability.CapabilityDetector
import com.professional.cam.camera.manager.CameraController
import com.professional.cam.camera.manager.Camera2Engine
import com.professional.cam.core.error.ErrorHandler
import com.professional.cam.core.error.ErrorRecoveryManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped

/**
 * 相机模块依赖注入
 *
 * 提供 Activity 级别的相机基础依赖：
 * - [CameraController]: 统一相机控制器（UI 层唯一入口）
 * - [Camera2Engine]: Camera2 专业相机引擎
 *
 * 设计原则：
 * - [Camera2Engine] 是 [android.hardware.camera2.CameraDevice]、
 *   [android.hardware.camera2.CameraCaptureSession] 的唯一管理者
 * - [CameraController] 仅负责协调调用和生命周期管理
 * - UI 层通过 [CameraController] 与相机交互，不直接访问 Camera2 API
 *
 * 注意：高级功能（录像、专业控制等）将在后续步骤添加。
 */
@Module
@InstallIn(ActivityComponent::class)
object CameraModule {

    @Provides
    @ActivityScoped
    fun provideCameraController(
        camera2Engine: Camera2Engine,
        capabilityDetector: CapabilityDetector,
        errorHandler: ErrorHandler,
        errorRecoveryManager: ErrorRecoveryManager
    ): CameraController {
        return CameraController(
            camera2Engine = camera2Engine,
            capabilityDetector = capabilityDetector,
            errorHandler = errorHandler,
            errorRecoveryManager = errorRecoveryManager
        )
    }

    @Provides
    @ActivityScoped
    fun provideCamera2Engine(
        @ApplicationContext context: Context,
        capabilityDetector: CapabilityDetector,
        errorHandler: ErrorHandler,
        errorRecoveryManager: ErrorRecoveryManager
    ): Camera2Engine {
        return Camera2Engine(
            context = context,
            capabilityDetector = capabilityDetector,
            errorHandler = errorHandler,
            errorRecoveryManager = errorRecoveryManager
        )
    }
}
