package com.professional.cam.core.error

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 错误处理中心
 *
 * 职责：
 * 1. 统一记录所有错误日志
 * 2. 判断错误是否可恢复
 * 3. 提供用户可读的错误消息
 */
@Singleton
class ErrorHandler @Inject constructor() {

    /**
     * 处理错误并返回处理结果
     */
    fun handle(error: AppError): ErrorResult {
        Timber.e(error, "AppError: ${error::class.simpleName} - ${error.message}")

        return when (error) {
            is AppError.CameraError -> handleCameraError(error)
            is AppError.RecordingError -> handleRecordingError(error)
            is AppError.SystemError -> handleSystemError(error)
        }
    }

    /**
     * 处理非 AppError 的通用异常
     */
    fun handleGeneric(error: Throwable): ErrorResult {
        Timber.e(error, "Unhandled exception: ${error.message}")
        return ErrorResult(
            isRecoverable = false,
            userMessage = "发生未知错误: ${error.localizedMessage ?: "未知错误"}",
            shouldNotifyUser = true
        )
    }

    private fun handleCameraError(error: AppError.CameraError): ErrorResult {
        return when (error) {
            is AppError.CameraError.OpenFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机打开失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RETRY_OPEN_CAMERA
            )
            is AppError.CameraError.Disconnected -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机已断开，正在重连...",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RECONNECT_CAMERA
            )
            is AppError.CameraError.SessionFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机会话异常，正在重建...",
                shouldNotifyUser = false,
                recoveryAction = RecoveryAction.RECREATE_SESSION
            )
            is AppError.CameraError.CapabilityUnsupported -> ErrorResult(
                isRecoverable = false,
                userMessage = "设备不支持: ${error.feature}",
                shouldNotifyUser = true
            )
            is AppError.CameraError.AccessDenied -> ErrorResult(
                isRecoverable = false,
                userMessage = "相机权限被拒绝，请在系统设置中授予相机权限",
                shouldNotifyUser = true
            )
            is AppError.CameraError.CameraInUse -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机被其他应用占用，请关闭其他相机应用后重试",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.WAIT_AND_RETRY
            )
            is AppError.CameraError.MaxCamerasInUse -> ErrorResult(
                isRecoverable = true,
                userMessage = "已达到最大相机数量限制",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.WAIT_AND_RETRY
            )
            is AppError.CameraError.CameraDisabled -> ErrorResult(
                isRecoverable = false,
                userMessage = "相机已被设备策略禁用",
                shouldNotifyUser = true
            )
            is AppError.CameraError.CameraDeviceError -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机设备错误 (${error.errorCode})，正在恢复...",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RETRY_OPEN_CAMERA
            )
            is AppError.CameraError.CameraServiceError -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机服务异常，正在重试...",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.WAIT_AND_RETRY
            )
            is AppError.CameraError.CameraNotFound -> ErrorResult(
                isRecoverable = false,
                userMessage = "找不到相机: ${error.cameraId}",
                shouldNotifyUser = true
            )
            is AppError.CameraError.PreviewSurfaceUnavailable -> ErrorResult(
                isRecoverable = true,
                userMessage = "预览 Surface 不可用",
                shouldNotifyUser = false,
                recoveryAction = RecoveryAction.RECREATE_SESSION
            )
            is AppError.CameraError.PreviewStartFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "预览启动失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RECREATE_SESSION
            )
            is AppError.CameraError.Recoverable -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机异常，正在恢复...",
                shouldNotifyUser = false,
                recoveryAction = RecoveryAction.RECREATE_SESSION
            )
            is AppError.CameraError.InitializationFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机初始化失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RETRY_OPEN_CAMERA
            )
            is AppError.CameraError.NotInitialized -> ErrorResult(
                isRecoverable = false,
                userMessage = "相机未初始化，请先调用初始化方法",
                shouldNotifyUser = true
            )
            is AppError.CameraError.CameraDisconnected -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机 ${error.cameraId} 已断开连接",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RECONNECT_CAMERA
            )
            is AppError.CameraError.CloseFailed -> ErrorResult(
                isRecoverable = false,
                userMessage = "相机关闭失败: ${error.reason}",
                shouldNotifyUser = false
            )
            is AppError.CameraError.SessionCreationFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机会话创建失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RECREATE_SESSION
            )
            is AppError.CameraError.SwitchFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机切换失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RETRY_OPEN_CAMERA
            )
            is AppError.CameraError.ReleaseFailed -> ErrorResult(
                isRecoverable = false,
                userMessage = "相机释放失败: ${error.reason}",
                shouldNotifyUser = false
            )
            is AppError.CameraError.SettingsApplyFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "相机参数设置失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RECREATE_SESSION
            )
            is AppError.CameraError.PhotoCaptureFailed -> ErrorResult(
                isRecoverable = false,
                userMessage = "拍照失败: ${error.reason}",
                shouldNotifyUser = true
            )
            is AppError.CameraError.PhotoSaveFailed -> ErrorResult(
                isRecoverable = false,
                userMessage = "照片保存失败: ${error.reason}",
                shouldNotifyUser = true
            )
        }
    }

    private fun handleRecordingError(error: AppError.RecordingError): ErrorResult {
        return when (error) {
            is AppError.RecordingError.InsufficientStorage -> ErrorResult(
                isRecoverable = false,
                userMessage = "存储空间不足，无法继续录像",
                shouldNotifyUser = true
            )
            is AppError.RecordingError.FileWriteFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "文件写入异常，已切换存储位置",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.SWITCH_STORAGE
            )
            is AppError.RecordingError.AudioInitFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "音频初始化失败，将录制无声视频",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.CONTINUE_WITHOUT_AUDIO
            )
            is AppError.RecordingError.EncodingFailed -> ErrorResult(
                isRecoverable = false,
                userMessage = "视频编码失败: ${error.reason}",
                shouldNotifyUser = true
            )
            is AppError.RecordingError.MaxDurationReached -> ErrorResult(
                isRecoverable = true,
                userMessage = "已达到最大录制时长，自动分段",
                shouldNotifyUser = false,
                recoveryAction = RecoveryAction.START_NEW_SEGMENT
            )
            is AppError.RecordingError.MaxFileSizeReached -> ErrorResult(
                isRecoverable = true,
                userMessage = "已达到文件大小限制，自动分段",
                shouldNotifyUser = false,
                recoveryAction = RecoveryAction.START_NEW_SEGMENT
            )
            is AppError.RecordingError.RecordingStartFailed -> ErrorResult(
                isRecoverable = true,
                userMessage = "录制启动失败: ${error.reason}",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.RETRY_OPEN_CAMERA
            )
            is AppError.RecordingError.RecordingSaveFailed -> ErrorResult(
                isRecoverable = false,
                userMessage = "视频保存失败: ${error.reason}",
                shouldNotifyUser = true
            )
        }
    }

    private fun handleSystemError(error: AppError.SystemError): ErrorResult {
        return when (error) {
            is AppError.SystemError.Overheating -> ErrorResult(
                isRecoverable = true,
                userMessage = "设备温度过高 (${error.temperature}°C)，正在降低录制质量",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.DOWNGRADE_QUALITY
            )
            is AppError.SystemError.LowBattery -> ErrorResult(
                isRecoverable = false,
                userMessage = "电量不足，即将停止录像",
                shouldNotifyUser = true
            )
            is AppError.SystemError.CpuThrottling -> ErrorResult(
                isRecoverable = true,
                userMessage = "CPU 负载过高，正在降低帧率",
                shouldNotifyUser = true,
                recoveryAction = RecoveryAction.DOWNGRADE_FPS
            )
            is AppError.SystemError.StorageLow -> ErrorResult(
                isRecoverable = false,
                userMessage = "存储空间不足 (剩余 ${error.availableBytes / 1024 / 1024}MB)",
                shouldNotifyUser = true
            )
        }
    }
}

/**
 * 错误处理结果
 */
data class ErrorResult(
    val isRecoverable: Boolean,
    val userMessage: String,
    val shouldNotifyUser: Boolean,
    val recoveryAction: RecoveryAction? = null
)

/**
 * 恢复动作枚举
 */
enum class RecoveryAction {
    RETRY_OPEN_CAMERA,
    RECONNECT_CAMERA,
    RECREATE_SESSION,
    SWITCH_STORAGE,
    CONTINUE_WITHOUT_AUDIO,
    START_NEW_SEGMENT,
    DOWNGRADE_QUALITY,
    DOWNGRADE_FPS,
    WAIT_AND_RETRY
}
