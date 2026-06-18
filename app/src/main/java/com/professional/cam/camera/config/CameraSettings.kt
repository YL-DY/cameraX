package com.professional.cam.camera.config

import android.hardware.camera2.CameraMetadata

/**
 * 统一相机参数模型
 *
 * 作为当前相机状态的唯一来源（Single Source of Truth）。
 * 所有参数修改必须经过此模型，最终由 [com.professional.cam.camera.manager.Camera2Engine.applySettings]
 * 统一构建 [android.hardware.camera2.CaptureRequest]。
 *
 * 设计原则：
 * - 不可变数据类，每次修改创建新实例
 * - 所有可选参数（ISO、曝光时间等）使用 nullable 表示"未设置/自动"
 * - 默认值为全自动模式
 *
 * @property exposureMode 曝光模式
 * @property iso ISO 感光度（null = 自动）
 * @property exposureTime 曝光时间（纳秒，null = 自动）
 * @property focusMode 对焦模式
 * @property focusDistance 对焦距离（米，null = 自动）
 * @property whiteBalanceMode 白平衡模式
 * @property zoomRatio 变焦倍率（1.0 = 无变焦）
 * @property flashMode 闪光灯模式
 */
data class CameraSettings(
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val iso: Int? = null,
    val exposureTime: Long? = null,
    val focusMode: FocusMode = FocusMode.AUTO,
    val focusDistance: Float? = null,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val zoomRatio: Float = 1.0f,
    val flashMode: FlashMode = FlashMode.OFF
) {
    companion object {
        /** 全自动模式默认值 */
        val DEFAULT = CameraSettings()
    }
}

/**
 * 曝光模式
 */
enum class ExposureMode {
    /** 全自动曝光 */
    AUTO,

    /** 手动 ISO + 自动快门 */
    MANUAL_ISO,

    /** 手动快门 + 自动 ISO */
    MANUAL_SHUTTER,

    /** 全手动（ISO + 快门） */
    MANUAL;

    /**
     * 是否需要手动 ISO
     */
    val requiresManualIso: Boolean
        get() = this == MANUAL_ISO || this == MANUAL

    /**
     * 是否需要手动快门
     */
    val requiresManualShutter: Boolean
        get() = this == MANUAL_SHUTTER || this == MANUAL
}

/**
 * 对焦模式
 */
enum class FocusMode {
    /** 自动对焦 */
    AUTO,

    /** 手动对焦 */
    MANUAL;

    /**
     * 转换为 Camera2 对焦模式常量
     */
    fun toCamera2Mode(): Int {
        return when (this) {
            AUTO -> CameraMetadata.CONTROL_AF_MODE_AUTO
            MANUAL -> CameraMetadata.CONTROL_AF_MODE_OFF
        }
    }
}

/**
 * 白平衡模式
 */
enum class WhiteBalanceMode {
    /** 自动白平衡 */
    AUTO,

    /** 日光（约 5200K） */
    DAYLIGHT,

    /** 阴天（约 6000K） */
    CLOUDY,

    /** 白炽灯（约 3200K） */
    INCANDESCENT,

    /** 荧光灯（约 4000K） */
    FLUORESCENT,

    /** 手动色温 */
    MANUAL;

    /**
     * 转换为 Camera2 AWB 模式常量
     */
    fun toCamera2Mode(): Int {
        return when (this) {
            AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
            CLOUDY -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            INCANDESCENT -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
            FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
            MANUAL -> CameraMetadata.CONTROL_AWB_MODE_OFF
        }
    }

    companion object {
        /**
         * 从 Camera2 AWB 模式常量转换
         */
        fun fromCamera2Mode(mode: Int): WhiteBalanceMode {
            return when (mode) {
                CameraMetadata.CONTROL_AWB_MODE_AUTO -> AUTO
                CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> DAYLIGHT
                CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> CLOUDY
                CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> INCANDESCENT
                CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> FLUORESCENT
                CameraMetadata.CONTROL_AWB_MODE_OFF -> MANUAL
                else -> AUTO
            }
        }
    }
}

/**
 * 闪光灯模式
 */
enum class FlashMode {
    /** 关闭 */
    OFF,

    /** 自动 */
    AUTO,

    /** 强制开启 */
    ON,

    /** 红眼 */
    RED_EYE,

    /** 常亮（手电筒模式） */
    TORCH;

    /**
     * 转换为 Camera2 闪光灯模式常量
     */
    fun toCamera2Mode(): Int {
        return when (this) {
            OFF -> CameraMetadata.FLASH_MODE_OFF
            AUTO -> CameraMetadata.FLASH_MODE_SINGLE
            ON -> CameraMetadata.FLASH_MODE_SINGLE
            RED_EYE -> CameraMetadata.FLASH_MODE_SINGLE
            TORCH -> CameraMetadata.FLASH_MODE_TORCH
        }
    }
}
