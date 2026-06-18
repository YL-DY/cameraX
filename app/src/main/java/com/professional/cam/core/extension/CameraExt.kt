package com.professional.cam.core.extension

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import android.view.Surface

/**
 * Camera2 相关扩展函数
 */

/** 获取硬件级别描述 */
val CameraCharacteristics.hardwareLevelString: String
    get() {
        return when (val level = get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            else -> "UNKNOWN($level)"
        }
    }

/** 获取支持的输出尺寸列表 */
fun CameraCharacteristics.getSupportedOutputSizes(format: Int = ImageFormat.YUV_420_888): List<Size> {
    val map = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
    return map.getOutputSizes(format)?.toList() ?: emptyList()
}

/** 获取支持的 FPS 范围 */
val CameraCharacteristics.supportedFpsRanges: List<Range<Int>>
    get() {
        return get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList()
            ?: emptyList()
    }

/** 获取最大数码变焦 */
val CameraCharacteristics.maxDigitalZoom: Float
    get() {
        return get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
    }

/** 判断是否支持光学防抖 */
val CameraCharacteristics.isOisSupported: Boolean
    get() {
        // LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION 返回 Boolean?，表示是否支持 OIS
        @Suppress("UNCHECKED_CAST")
        return (get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) as? Boolean) ?: false
    }

/** 获取传感器方向 */
val CameraCharacteristics.sensorOrientation: Int
    get() = get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

/** 判断是否为逻辑相机（多摄合一） */
val CameraCharacteristics.isLogicalCamera: Boolean
    get() {
        val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        return capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
    }

/** 获取可用焦距列表 */
val CameraCharacteristics.availableFocalLengths: FloatArray?
    get() = get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

/** 获取传感器像素尺寸 */
val CameraCharacteristics.sensorPixelArraySize: Size?
    get() = get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)

/** 判断是否支持 RAW */
val CameraCharacteristics.isRawSupported: Boolean
    get() {
        val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        return capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
    }

/** 获取 ISO 范围 */
val CameraCharacteristics.isoRange: Range<Int>?
    get() = get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

/** 获取快门速度范围 */
val CameraCharacteristics.shutterSpeedRange: Range<Long>?
    get() = get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

/** 获取曝光补偿范围 */
val CameraCharacteristics.exposureCompensationRange: Range<Int>?
    get() = get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)

/** 获取曝光补偿步长 */
val CameraCharacteristics.exposureCompensationStep: Float?
    get() {
        val rational = get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return null
        return rational.toFloat()
    }

/** 获取最小对焦距离 */
val CameraCharacteristics.minFocusDistance: Float
    get() = get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

/** 判断是否支持手动对焦 */
val CameraCharacteristics.isManualFocusSupported: Boolean
    get() {
        val modes = get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        return modes.contains(CaptureRequest.CONTROL_AF_MODE_OFF)
    }

/** 判断是否支持 AE 手动模式 */
val CameraCharacteristics.isManualExposureSupported: Boolean
    get() {
        val modes = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        return modes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
    }

/** 获取支持的 AWB 模式 */
val CameraCharacteristics.awbModes: IntArray?
    get() = get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)

/** 获取白平衡色温范围 */
val CameraCharacteristics.whiteBalanceTemperatureRange: Range<Int>?
    get() = get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)?.let { null }
    // 色温范围通常通过 COLOR_CORRECTION_GAINS 和 AWB 模式控制
    // 实际范围需要根据设备查询

/** 获取闪光灯是否可用 */
val CameraCharacteristics.isFlashAvailable: Boolean
    get() = get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

/** 获取最大视频帧率 */
fun CameraCharacteristics.getMaxVideoFps(): Int {
    val fpsRanges = supportedFpsRanges
    return fpsRanges.maxOfOrNull { it.upper } ?: 30
}

/** 获取最佳视频尺寸 */
fun CameraCharacteristics.getBestVideoSize(targetWidth: Int = 1920): Size? {
    val map = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
    val videoSizes = map.getOutputSizes(MediaRecorder::class.java)?.toList() ?: return null
    
    return videoSizes
        .filter { it.width <= targetWidth }
        .maxByOrNull { it.width * it.height }
}

/** 将 CaptureResult 中的值转为可读格式 */
fun CaptureResult.getIso(): Int? {
    return get(CaptureResult.SENSOR_SENSITIVITY)
}

fun CaptureResult.getShutterSpeed(): Long? {
    return get(CaptureResult.SENSOR_EXPOSURE_TIME)
}

fun CaptureResult.getAeMode(): Int? {
    return get(CaptureResult.CONTROL_AE_MODE)
}

fun CaptureResult.getAfMode(): Int? {
    return get(CaptureResult.CONTROL_AF_MODE)
}

fun CaptureResult.getAwbMode(): Int? {
    return get(CaptureResult.CONTROL_AWB_MODE)
}

fun CaptureResult.getFocusDistance(): Float? {
    return get(CaptureResult.LENS_FOCUS_DISTANCE)
}

// 避免 MediaRecorder 未导入的问题
private class MediaRecorder {
    companion object {
        const val CLASS = "android.media.MediaRecorder"
    }
}
