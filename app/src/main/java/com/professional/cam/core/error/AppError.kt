package com.professional.cam.core.error

/**
 * 应用错误层级定义
 *
 * 所有错误继承自 [AppError]，按模块分类：
 * - [CameraError]: 相机相关错误
 * - [RecordingError]: 录像相关错误（后续步骤）
 * - [SystemError]: 系统监控相关错误（后续步骤）
 */
sealed class AppError : Throwable() {

    /** 相机错误 */
    sealed class CameraError : AppError() {
        /** 相机打开失败 */
        data class OpenFailed(
            val cameraId: String,
            val reason: String
        ) : CameraError()

        /** 相机断开连接 */
        data class Disconnected(
            val cameraId: String
        ) : CameraError()

        /** 相机会话创建失败 */
        data class SessionFailed(
            val reason: String
        ) : CameraError()

        /** 设备不支持该功能 */
        data class CapabilityUnsupported(
            val feature: String
        ) : CameraError()

        /** 相机权限被拒绝 */
        data object AccessDenied : CameraError()

        /** 相机被其他应用占用 */
        data object CameraInUse : CameraError()

        /** 已达到最大相机数量限制 */
        data object MaxCamerasInUse : CameraError()

        /** 相机被设备策略禁用 */
        data object CameraDisabled : CameraError()

        /** 相机设备致命错误 */
        data class CameraDeviceError(
            val errorCode: Int
        ) : CameraError()

        /** 相机服务致命错误 */
        data object CameraServiceError : CameraError()

        /** 相机 ID 不存在 */
        data class CameraNotFound(
            val cameraId: String
        ) : CameraError()

        /** 预览 Surface 不可用 */
        data object PreviewSurfaceUnavailable : CameraError()

        /** 预览启动失败 */
        data class PreviewStartFailed(
            val reason: String
        ) : CameraError()

        /** 可恢复的相机错误 */
        data class Recoverable(
            val error: Throwable
        ) : CameraError()

        /** 相机初始化失败 */
        data class InitializationFailed(
            val reason: String
        ) : CameraError()

        /** 相机未初始化即调用操作 */
        data object NotInitialized : CameraError()

        /** 相机断开连接（别名，用于 CameraController 层） */
        data class CameraDisconnected(
            val cameraId: String
        ) : CameraError()

        /** 相机关闭失败 */
        data class CloseFailed(
            val reason: String
        ) : CameraError()

        /** 相机会话创建失败（别名，用于 CameraController 层） */
        data class SessionCreationFailed(
            val reason: String
        ) : CameraError()

        /** 相机切换失败 */
        data class SwitchFailed(
            val cameraId: String,
            val reason: String
        ) : CameraError()

        /** 相机释放失败 */
        data class ReleaseFailed(
            val reason: String
        ) : CameraError()

        /** 相机设置应用失败 */
        data class SettingsApplyFailed(
            val reason: String
        ) : CameraError()

        /** 拍照失败 */
        data class PhotoCaptureFailed(
            val reason: String
        ) : CameraError()

        /** 照片保存失败 */
        data class PhotoSaveFailed(
            val reason: String
        ) : CameraError()
    }

    /** 录像错误 */
    sealed class RecordingError : AppError() {
        data object InsufficientStorage : RecordingError()

        data class FileWriteFailed(
            val path: String,
            val error: Throwable? = null
        ) : RecordingError()

        data object AudioInitFailed : RecordingError()

        data class EncodingFailed(
            val reason: String
        ) : RecordingError()

        data object MaxDurationReached : RecordingError()

        data object MaxFileSizeReached : RecordingError()

        /** 录制启动失败 */
        data class RecordingStartFailed(
            val reason: String
        ) : RecordingError()

        /** 录制保存到 MediaStore 失败 */
        data class RecordingSaveFailed(
            val path: String,
            val reason: String
        ) : RecordingError()
    }

    /** 系统错误（后续步骤实现） */
    sealed class SystemError : AppError() {
        data class Overheating(
            val temperature: Float
        ) : SystemError()

        data object LowBattery : SystemError()

        data object CpuThrottling : SystemError()

        data class StorageLow(
            val availableBytes: Long
        ) : SystemError()
    }
}
