package com.professional.cam.camera.capability

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import android.util.Size

/**
 * 相机能力数据模型
 *
 * 由 [CapabilityDetector] 在启动时检测并缓存。
 * 所有专业功能基于此模型做显示/隐藏/降级决策。
 * 不允许出现品牌硬编码判断。
 *
 * @property cameraId 相机 ID
 * @property hardwareLevel 硬件级别 (INFO_3, FULL, LEVEL_3, LIMITED, LEGACY)
 * @property facing 摄像头方向 (BACK, FRONT, EXTERNAL)
 */
data class CameraCapabilities(
    // ── 基础信息 ──
    val cameraId: String,
    val hardwareLevel: Int,
    val facing: Int,

    // ── RAW ──
    val isRawSupported: Boolean,
    val rawSize: Size?,

    // ── 手动对焦 ──
    val isManualFocusSupported: Boolean,
    val minFocusDistance: Float,

    // ── 手动曝光 (ISO) ──
    val isManualIsoSupported: Boolean,
    val isoRange: Range<Int>?,

    // ── 手动快门 ──
    val isManualShutterSupported: Boolean,
    val shutterSpeedRange: Range<Long>?,

    // ── 白平衡 ──
    val whiteBalanceModes: IntArray?,
    val whiteBalanceTemperatureRange: Range<Int>?,

    // ── 曝光补偿 ──
    val isExposureCompensationSupported: Boolean,
    val exposureCompensationRange: Range<Int>?,
    val exposureCompensationStep: Float?,

    // ── 闪光灯 ──
    val flashModes: IntArray?,

    // ── 变焦 ──
    val isZoomSupported: Boolean,
    val maxZoomRatio: Float,
    val availableFocalLengths: FloatArray?,

    // ── 多摄 ──
    val isLogicalCamera: Boolean,
    val physicalCameraIds: List<String>?,
    val isUltraWideAvailable: Boolean,
    val isTelephotoAvailable: Boolean,
    val isMacroAvailable: Boolean,

    // ── 帧率 ──
    val supportedFpsRanges: Array<Range<Int>>,
    val maxCaptureFps: Int,
    val maxVideoFps: Int,

    // ── 防抖 ──
    val isOisSupported: Boolean,
    val isEisSupported: Boolean,
    val stabilizationModes: IntArray?,

    // ── 视频 ──
    val supportedVideoSizes: List<Size>,
    val supportedVideoCodecs: List<Int>,

    // ── 传感器 ──
    val sensorOrientation: Int,
    val sensorSize: Size?,
    val pixelArraySize: Size?,

    // ── 其他 ──
    val supportedNoiseReductionModes: IntArray?,
    val supportedEdgeModes: IntArray?,
    val availableColorEffects: IntArray?,
    val maxRegions: Int
) {
    /** 硬件级别描述 */
    val hardwareLevelString: String
        get() = when (hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            else -> "UNKNOWN"
        }

    /** 判断是否为全功能相机 */
    val isFullCapability: Boolean
        get() = hardwareLevel >= CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraCapabilities) return false
        return cameraId == other.cameraId
    }

    override fun hashCode(): Int = cameraId.hashCode()
}

/**
 * 相机功能枚举
 * 用于 [CapabilityDetector.isFeatureSupported] 查询
 */
enum class CameraFeature {
    RAW,
    MANUAL_FOCUS,
    MANUAL_ISO,
    MANUAL_SHUTTER,
    WHITE_BALANCE,
    FLASH,
    ZOOM,
    ULTRA_WIDE,
    TELEPHOTO,
    MACRO,
    HIGH_FRAME_RATE,
    OIS,
    EIS,
    LOG_VIDEO,
    EXPOSURE_COMPENSATION,
    NOISE_REDUCTION
}
