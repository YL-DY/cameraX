package com.professional.cam.core.di

import android.content.Context
import com.professional.cam.camera.audio.AudioRecorder
import com.professional.cam.camera.capability.CapabilityDetector
import com.professional.cam.camera.capture.CaptureResultProcessor
import com.professional.cam.camera.capture.StillCaptureManager
import com.professional.cam.camera.manager.CameraController
import com.professional.cam.camera.manager.Camera2Engine
import com.professional.cam.camera.recorder.VideoRecorderManager
import com.professional.cam.camera.video.VideoEncoder
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
 * - [StillCaptureManager]: 拍照管理（ImageReader + CaptureRequest）
 * - [CaptureResultProcessor]: 拍照结果保存（MediaStore + EXIF）
 * - [VideoRecorderManager]: 视频录制管理（MediaMuxer + 状态机）
 *
 * 设计原则：
 * - [Camera2Engine] 是 [android.hardware.camera2.CameraDevice]、
 *   [android.hardware.camera2.CameraCaptureSession] 的唯一管理者
 * - [CameraController] 仅负责协调调用和生命周期管理
 * - UI 层通过 [CameraController] 与相机交互，不直接访问 Camera2 API
 */
@Module
@InstallIn(ActivityComponent::class)
object CameraModule {

    @Provides
    @ActivityScoped
    fun provideStillCaptureManager(): StillCaptureManager {
        return StillCaptureManager()
    }

    @Provides
    @ActivityScoped
    fun provideCaptureResultProcessor(
        @ApplicationContext context: Context
    ): CaptureResultProcessor {
        return CaptureResultProcessor(context)
    }

    @Provides
    @ActivityScoped
    fun provideVideoEncoder(): VideoEncoder {
        return VideoEncoder()
    }

    @Provides
    @ActivityScoped
    fun provideAudioRecorder(): AudioRecorder {
        return AudioRecorder()
    }

    @Provides
    @ActivityScoped
    fun provideVideoRecorderManager(
        videoEncoder: VideoEncoder,
        audioRecorder: AudioRecorder
    ): VideoRecorderManager {
        return VideoRecorderManager(
            videoEncoder = videoEncoder,
            audioRecorder = audioRecorder
        )
    }

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
        errorRecoveryManager: ErrorRecoveryManager,
        stillCaptureManager: StillCaptureManager,
        videoRecorderManager: VideoRecorderManager
    ): Camera2Engine {
        return Camera2Engine(
            context = context,
            capabilityDetector = capabilityDetector,
            errorHandler = errorHandler,
            errorRecoveryManager = errorRecoveryManager,
            stillCaptureManager = stillCaptureManager,
            videoRecorderManager = videoRecorderManager
        )
    }
}
