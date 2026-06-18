package com.professional.cam.camera.manager

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.util.Range
import android.view.Surface
import com.professional.cam.camera.capability.CameraCapability
import com.professional.cam.camera.config.CameraSettings
import com.professional.cam.camera.config.ExposureMode
import com.professional.cam.camera.config.FlashMode
import com.professional.cam.camera.config.FocusMode
import com.professional.cam.camera.config.WhiteBalanceMode
import com.professional.cam.core.util.Logger

/**
 * 预览 CaptureRequest 统一构建器
 *
 * 职责：
 * - 根据 [CameraSettings] 构建 [CaptureRequest]
 * - 所有参数修改集中在此类完成
 * - 禁止多个类直接修改 [CaptureRequest.Builder]
 *
 * 设计原则：
 * - 每次调用 [build] 创建新的 [CaptureRequest.Builder]
 * - 不修改 [Camera2Engine] 持有的 builder 引用
 * - 所有 Camera2 常量统一封装，避免魔法值
 *
 * @param cameraDevice CameraDevice 实例
 * @param surface 预览 Surface
 * @param cameraCapability 相机能力（用于参数范围校验）
 */
class PreviewRequestBuilder(
    private val cameraDevice: CameraDevice,
    private val surface: Surface,
    private val cameraCapability: CameraCapability? = null
) {

    /**
     * 根据 [CameraSettings] 构建预览 [CaptureRequest]
     *
     * @param settings 当前相机参数设置
     * @return 构建完成的 [CaptureRequest]
     */
    fun build(settings: CameraSettings): CaptureRequest {
        val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)

        // ── 1. 曝光控制 ──
        applyExposureSettings(builder, settings)

        // ── 2. 对焦控制 ──
        applyFocusSettings(builder, settings)

        // ── 3. 白平衡控制 ──
        applyWhiteBalanceSettings(builder, settings)

        // ── 4. 变焦控制 ──
        applyZoomSettings(builder, settings)

        // ── 5. 闪光灯控制 ──
        applyFlashSettings(builder, settings)

        // ── 6. 基础控制模式 ──
        applyControlModes(builder, settings)

        return builder.build()
    }

    /**
     * 应用曝光参数
     */
    private fun applyExposureSettings(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        when (settings.exposureMode) {
            ExposureMode.AUTO -> {
                // 自动曝光：AE 开启，ISO 和快门由相机自动控制
                builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                builder.set(
                    CaptureRequest.CONTROL_AE_LOCK,
                    false
                )
            }

            ExposureMode.MANUAL_ISO -> {
                // 手动 ISO + 自动快门
                builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                settings.iso?.let { iso ->
                    val clampedIso = clampIso(iso)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)
                    Logger.d(
                        Logger.Tag.CAMERA,
                        "PreviewRequestBuilder: set ISO=$clampedIso"
                    )
                }
            }

            ExposureMode.MANUAL_SHUTTER -> {
                // 手动快门 + 自动 ISO
                builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                settings.exposureTime?.let { time ->
                    val clampedTime = clampExposureTime(time)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedTime)
                    Logger.d(
                        Logger.Tag.CAMERA,
                        "PreviewRequestBuilder: set exposureTime=${clampedTime}ns"
                    )
                }
            }

            ExposureMode.MANUAL -> {
                // 全手动：ISO + 快门
                builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                settings.iso?.let { iso ->
                    val clampedIso = clampIso(iso)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)
                }
                settings.exposureTime?.let { time ->
                    val clampedTime = clampExposureTime(time)
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedTime)
                }
            }
        }
    }

    /**
     * 应用对焦参数
     */
    private fun applyFocusSettings(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        when (settings.focusMode) {
            FocusMode.AUTO -> {
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO
                )
                // 触发自动对焦
                builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE
                )
            }

            FocusMode.MANUAL -> {
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                settings.focusDistance?.let { distance ->
                    val clampedDistance = clampFocusDistance(distance)
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDistance)
                    Logger.d(
                        Logger.Tag.CAMERA,
                        "PreviewRequestBuilder: set focusDistance=$clampedDistance"
                    )
                }
            }
        }
    }

    /**
     * 应用白平衡参数
     */
    private fun applyWhiteBalanceSettings(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        when (settings.whiteBalanceMode) {
            WhiteBalanceMode.AUTO -> {
                builder.set(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO
                )
                builder.set(
                    CaptureRequest.CONTROL_AWB_LOCK,
                    false
                )
            }

            WhiteBalanceMode.MANUAL -> {
                // 手动白平衡：关闭 AWB，由外部设置色温
                builder.set(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF
                )
            }

            else -> {
                builder.set(
                    CaptureRequest.CONTROL_AWB_MODE,
                    settings.whiteBalanceMode.toCamera2Mode()
                )
            }
        }
    }

    /**
     * 应用变焦参数
     */
    private fun applyZoomSettings(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        val zoomRatio = settings.zoomRatio.coerceAtLeast(1.0f)
        if (zoomRatio > 1.0001f) {
            // 需要变焦时设置 SCALER_CROP_REGION
            // 实际 cropRegion 由 Camera2Engine 根据 zoomRatio 计算
            builder.set(
                CaptureRequest.CONTROL_ZOOM_RATIO,
                zoomRatio
            )
        } else {
            builder.set(
                CaptureRequest.CONTROL_ZOOM_RATIO,
                1.0f
            )
        }
    }

    /**
     * 应用闪光灯参数
     */
    private fun applyFlashSettings(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        when (settings.flashMode) {
            FlashMode.OFF -> {
                builder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF
                )
            }

            FlashMode.AUTO -> {
                builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                )
            }

            FlashMode.ON, FlashMode.RED_EYE -> {
                builder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_SINGLE
                )
            }

            FlashMode.TORCH -> {
                builder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_TORCH
                )
            }
        }
    }

    /**
     * 应用基础控制模式
     *
     * 设置 3A 控制模式（AE/AWB/AF）的默认值，
     * 确保在手动模式下其他自动功能正确关闭。
     */
    private fun applyControlModes(
        builder: CaptureRequest.Builder,
        settings: CameraSettings
    ) {
        // 如果 AE 已关闭（手动 ISO），设置 SENSOR 模式
        if (settings.exposureMode.requiresManualIso) {
            builder.set(
                CaptureRequest.SENSOR_SENSITIVITY,
                settings.iso ?: 100
            )
        }

        // 设置 AE 防闪烁模式（仅在 AE 开启时有效）
        if (!settings.exposureMode.requiresManualIso) {
            builder.set(
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
            )
        }
    }

    // ── 参数范围钳制 ──

    private fun clampIso(iso: Int): Int {
        val range = cameraCapability?.isoRange ?: return iso.coerceIn(100, 3200)
        return iso.coerceIn(range.lower, range.upper)
    }

    private fun clampExposureTime(time: Long): Long {
        val range = cameraCapability?.exposureTimeRange ?: return time.coerceIn(
            100000L, 30000000L
        )
        return time.coerceIn(range.lower, range.upper)
    }

    private fun clampFocusDistance(distance: Float): Float {
        val range = cameraCapability?.focusDistanceRange ?: return distance.coerceIn(0f, 1f)
        return distance.coerceIn(range.lower, range.upper)
    }
}
