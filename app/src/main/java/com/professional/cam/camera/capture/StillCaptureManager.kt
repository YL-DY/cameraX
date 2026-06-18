package com.professional.cam.camera.capture

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.view.Surface
import com.professional.cam.camera.config.CameraSettings
import com.professional.cam.core.util.Logger
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 静态图像捕获管理器
 *
 * 职责：
 * - 创建和管理 [ImageReader] 实例
 * - 构建拍照 [CaptureRequest]（使用 [CameraDevice.TEMPLATE_STILL_CAPTURE]）
 * - 接收拍照结果并回调 JPEG 数据
 * - 拍照完成后自动恢复预览请求
 *
 * 设计原则：
 * - 独立模块，不将拍照逻辑写入 [Camera2Engine]
 * - [ImageReader] 生命周期完全由此类管理
 * - 防止 [Image] 泄漏（确保每个 Image 被 close）
 * - 支持连续拍照，不重建 [CameraCaptureSession]
 *
 * @property maxImages ImageReader 最大图像缓冲数
 */
@Singleton
class StillCaptureManager @Inject constructor() {

    companion object {
        private const val TAG = "StillCapture"
        private const val MAX_IMAGES = 2
        private const val JPEG_QUALITY = 100
    }

    // ── ImageReader ──
    private var imageReader: ImageReader? = null

    // ── 当前拍照状态 ──
    @Volatile
    private var isCapturing = false

    // ── 拍照结果回调 ──
    private var onPhotoResultCallback: ((PhotoResult) -> Unit)? = null

    // ── 预览恢复回调（由 Camera2Engine 设置） ──
    private var onRestorePreviewCallback: (() -> Unit)? = null

    /**
     * 初始化 ImageReader
     *
     * 根据指定尺寸创建 JPEG 格式的 [ImageReader]。
     * 如果已有 ImageReader 且尺寸匹配，则复用。
     * 如果尺寸不匹配，先关闭旧的再创建新的。
     *
     * @param width 图像宽度
     * @param height 图像高度
     */
    fun initializeImageReader(width: Int, height: Int) {
        val current = imageReader
        if (current != null) {
            if (current.width == width && current.height == height) {
                Logger.d(TAG, "ImageReader already initialized with same size: ${width}x$height")
                return
            }
            Logger.d(TAG, "ImageReader size changed, reinitializing: ${current.width}x$current.height -> ${width}x$height")
            current.close()
        }

        Logger.d(TAG, "Initializing ImageReader: ${width}x$height, format=JPEG, maxImages=$MAX_IMAGES")
        imageReader = ImageReader.newInstance(
            width, height,
            ImageFormat.JPEG, MAX_IMAGES
        ).also { reader ->
            reader.setOnImageAvailableListener(
                { onImageAvailable(it) },
                null
            )
        }
    }

    /**
     * 获取 ImageReader 的 Surface
     *
     * 用于添加到拍照 [CaptureRequest] 的 target 列表。
     *
     * @return ImageReader 的 Surface，如果未初始化返回 null
     */
    fun getReaderSurface(): Surface? = imageReader?.surface

    /**
     * 获取 ImageReader 的宽度
     */
    fun getReaderWidth(): Int = imageReader?.width ?: 0

    /**
     * 获取 ImageReader 的高度
     */
    fun getReaderHeight(): Int = imageReader?.height ?: 0

    /**
     * 执行拍照
     *
     * 使用 [CameraDevice.TEMPLATE_STILL_CAPTURE] 构建拍照请求，
     * 继承当前 [CameraSettings] 中的参数，
     * 通过 [CameraCaptureSession.capture] 执行单帧捕获。
     *
     * 拍照完成后自动通过 [onRestorePreviewCallback] 恢复预览。
     *
     * @param session 当前 CameraCaptureSession
     * @param device 当前 CameraDevice
     * @param settings 当前相机设置（用于继承参数）
     * @param jpegOrientation JPEG 方向值（来自传感器方向）
     * @param onResult 拍照结果回调
     */
    fun captureStillImage(
        session: CameraCaptureSession,
        device: CameraDevice,
        settings: CameraSettings,
        jpegOrientation: Int,
        onResult: (PhotoResult) -> Unit
    ) {
        if (isCapturing) {
            Logger.w(TAG, "Already capturing, ignoring duplicate request")
            onResult(PhotoResult.Error("Already capturing"))
            return
        }

        val reader = imageReader
        if (reader == null) {
            Logger.e(TAG, "ImageReader not initialized")
            onResult(PhotoResult.Error("ImageReader not initialized"))
            return
        }

        isCapturing = true
        onPhotoResultCallback = onResult

        try {
            // 使用 TEMPLATE_STILL_CAPTURE 构建拍照请求
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)

            // 继承当前 CameraSettings 中的参数
            applySettingsToCaptureRequest(captureBuilder, settings)

            // 设置 JPEG 方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

            // 设置 JPEG 质量
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY.toByte())

            val captureRequest = captureBuilder.build()

            Logger.d(TAG, "Executing still capture, orientation=$jpegOrientation")

            session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Logger.d(TAG, "Still capture completed, waiting for ImageReader callback")
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure
                ) {
                    super.onCaptureFailed(session, request, failure)
                    Logger.e(TAG, "Still capture failed: ${failure.reason}")
                    isCapturing = false
                    onPhotoResultCallback?.invoke(
                        PhotoResult.Error("Capture failed: ${failure.reason}")
                    )
                    onPhotoResultCallback = null
                    // 恢复预览
                    restorePreview()
                }
            }, null)

        } catch (e: Exception) {
            Logger.e(TAG, e, "Failed to execute still capture")
            isCapturing = false
            onPhotoResultCallback?.invoke(
                PhotoResult.Error("Capture exception: ${e.message}")
            )
            onPhotoResultCallback = null
            restorePreview()
        }
    }

    /**
     * 设置预览恢复回调
     *
     * 由 [Camera2Engine] 设置，用于拍照完成后恢复预览请求。
     */
    fun setOnRestorePreviewCallback(callback: () -> Unit) {
        onRestorePreviewCallback = callback
    }

    /**
     * 释放所有资源
     *
     * 关闭 ImageReader，清理回调引用。
     */
    fun release() {
        Logger.d(TAG, "Releasing StillCaptureManager")
        imageReader?.close()
        imageReader = null
        onPhotoResultCallback = null
        onRestorePreviewCallback = null
        isCapturing = false
    }

    /**
     * ImageReader 图像可用回调
     *
     * 从 [ImageReader] 中获取 JPEG 数据，关闭 Image 防止泄漏，
     * 通过回调返回 [PhotoResult.Success]。
     */
    private fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return

        try {
            val jpegData = imageToJpegBytes(image)
            val width = image.width
            val height = image.height

            Logger.d(TAG, "Image captured: ${width}x$height, size=${jpegData.size} bytes")

            isCapturing = false
            onPhotoResultCallback?.invoke(
                PhotoResult.Success(
                    data = jpegData,
                    width = width,
                    height = height
                )
            )
            onPhotoResultCallback = null

            // 恢复预览
            restorePreview()
        } catch (e: Exception) {
            Logger.e(TAG, e, "Failed to process captured image")
            isCapturing = false
            onPhotoResultCallback?.invoke(
                PhotoResult.Error("Image processing failed: ${e.message}")
            )
            onPhotoResultCallback = null
            restorePreview()
        } finally {
            // 必须关闭 Image 以防止泄漏
            image.close()
        }
    }

    /**
     * 将 [Image] 转换为 JPEG 字节数组
     *
     * JPEG 格式的 Image 只有一个平面（plane 0），
     * 直接读取 buffer 即可。
     */
    private fun imageToJpegBytes(image: Image): ByteArray {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    /**
     * 恢复预览请求
     *
     * 拍照完成后调用，通知 [Camera2Engine] 恢复预览。
     */
    private fun restorePreview() {
        Logger.d(TAG, "Restoring preview after capture")
        onRestorePreviewCallback?.invoke()
    }

    /**
     * 将当前 [CameraSettings] 应用到拍照 [CaptureRequest]
     *
     * 继承预览中的参数设置，确保拍照结果与预览效果一致。
     */
    private fun applySettingsToCaptureRequest(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        // 曝光控制
        when (settings.exposureMode) {
            com.professional.cam.camera.config.ExposureMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            com.professional.cam.camera.config.ExposureMode.MANUAL_ISO -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                settings.iso?.let { builder.set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            }
            com.professional.cam.camera.config.ExposureMode.MANUAL_SHUTTER -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                settings.exposureTime?.let { builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
            }
            com.professional.cam.camera.config.ExposureMode.MANUAL -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                settings.iso?.let { builder.set(CaptureRequest.SENSOR_SENSITIVITY, it) }
                settings.exposureTime?.let { builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
            }
        }

        // 对焦控制
        when (settings.focusMode) {
            com.professional.cam.camera.config.FocusMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            }
            com.professional.cam.camera.config.FocusMode.MANUAL -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                settings.focusDistance?.let { builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, it) }
            }
        }

        // 白平衡控制
        when (settings.whiteBalanceMode) {
            com.professional.cam.camera.config.WhiteBalanceMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }
            else -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, settings.whiteBalanceMode.toCamera2Mode())
            }
        }

        // 变焦控制
        if (settings.zoomRatio != 1.0f) {
            val cropRegion = android.graphics.Rect()
            // 变焦通过 SCALER_CROP_REGION 实现
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
        }

        // 闪光灯控制
        builder.set(CaptureRequest.FLASH_MODE, settings.flashMode.toCamera2Mode())
    }
}

/**
 * 拍照结果
 */
sealed class PhotoResult {
    /**
     * 拍照成功
     *
     * @property data JPEG 字节数据
     * @property width 图像宽度
     * @property height 图像高度
     */
    data class Success(
        val data: ByteArray,
        val width: Int,
        val height: Int
    ) : PhotoResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return data.contentEquals(other.data) &&
                    width == other.width &&
                    height == other.height
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }

    /**
     * 拍照失败
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : PhotoResult()
}
