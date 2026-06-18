package com.professional.cam.camera.capability

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Range
import android.util.Size
import com.professional.cam.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备能力检测器
 *
 * 核心职责：
 * 1. 读取所有相机的 [CameraCharacteristics]
 * 2. 构建完整的 [CameraCapabilities] 模型
 * 3. 缓存检测结果，避免重复读取
 * 4. 提供功能查询接口 [isFeatureSupported]
 *
 * 设计原则：
 * - 所有兼容性判断基于 CameraCharacteristics，无品牌硬编码
 * - 不支持的功能必须优雅降级，不允许 Crash
 * - 检测结果在应用生命周期内缓存
 */
@Singleton
class CapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cache = ConcurrentHashMap<String, CameraCapabilities>()

    /**
     * 检测所有可用相机的能力
     */
    fun detectAllCameras(): List<CameraCapabilities> {
        val cameraIds = try {
            cameraManager.cameraIdList
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAPABILITY, e, "Failed to get camera ID list")
            return emptyList()
        }

        return cameraIds.mapNotNull { cameraId ->
            try {
                detectCamera(cameraId)
            } catch (e: Exception) {
                Logger.e(Logger.Tag.CAPABILITY, e, "Failed to detect camera: $cameraId")
                null
            }
        }
    }

    /**
     * 检测指定相机的能力
     */
    fun detectCamera(cameraId: String): CameraCapabilities {
        cache[cameraId]?.let { return it }

        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAPABILITY, e, "Failed to get characteristics for: $cameraId")
            throw e
        }

        val capabilities = buildCapabilities(cameraId, characteristics)
        cache[cameraId] = capabilities
        return capabilities
    }

    /**
     * 获取缓存的相机能力
     */
    fun getCapabilities(cameraId: String): CameraCapabilities? {
        return cache[cameraId]
    }

    /**
     * 获取所有相机 ID
     */
    fun getAllCameraIds(): List<String> {
        return try {
            cameraManager.cameraIdList.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取后置相机 ID 列表
     */
    fun getBackCameraIds(): List<String> {
        return getAllCameraIds().filter { cameraId ->
            val facing = try {
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING)
            } catch (e: Exception) {
                null
            }
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    /**
     * 获取前置相机 ID 列表
     */
    fun getFrontCameraIds(): List<String> {
        return getAllCameraIds().filter { cameraId ->
            val facing = try {
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING)
            } catch (e: Exception) {
                null
            }
            facing == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    /**
     * 查询特定功能是否支持
     */
    fun isFeatureSupported(cameraId: String, feature: CameraFeature): Boolean {
        val caps = getCapabilities(cameraId) ?: return false
        return when (feature) {
            CameraFeature.RAW -> caps.isRawSupported
            CameraFeature.MANUAL_FOCUS -> caps.isManualFocusSupported
            CameraFeature.MANUAL_ISO -> caps.isManualIsoSupported
            CameraFeature.MANUAL_SHUTTER -> caps.isManualShutterSupported
            CameraFeature.WHITE_BALANCE -> caps.whiteBalanceModes != null
            CameraFeature.FLASH -> caps.flashModes != null
            CameraFeature.ZOOM -> caps.isZoomSupported
            CameraFeature.ULTRA_WIDE -> caps.isUltraWideAvailable
            CameraFeature.TELEPHOTO -> caps.isTelephotoAvailable
            CameraFeature.MACRO -> caps.isMacroAvailable
            CameraFeature.HIGH_FRAME_RATE -> caps.maxVideoFps >= 60
            CameraFeature.OIS -> caps.isOisSupported
            CameraFeature.EIS -> caps.isEisSupported
            CameraFeature.LOG_VIDEO -> false // Phase 1 暂不支持
            CameraFeature.EXPOSURE_COMPENSATION -> caps.isExposureCompensationSupported
            CameraFeature.NOISE_REDUCTION -> caps.supportedNoiseReductionModes != null
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        cache.clear()
    }

    // ── 私有方法 ──

    private fun buildCapabilities(
        cameraId: String,
        characteristics: CameraCharacteristics
    ): CameraCapabilities {
        val hardwareLevel = characteristics.get(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
        ) ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED

        val facing = characteristics.get(
            CameraCharacteristics.LENS_FACING
        ) ?: CameraCharacteristics.LENS_FACING_BACK

        val streamMap = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )

        val capabilities = characteristics.get(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        ) ?: intArrayOf()

        // RAW
        val isRawSupported = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )
        val rawSize = if (isRawSupported) {
            streamMap?.getOutputSizes(ImageFormat.RAW_SENSOR)?.firstOrNull()
        } else null

        // 手动对焦
        val afModes = characteristics.get(
            CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
        ) ?: intArrayOf()
        val isManualFocusSupported = afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF)
        val minFocusDistance = characteristics.get(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f

        // 手动 ISO
        val aeModes = characteristics.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
        ) ?: intArrayOf()
        val isManualIsoSupported = aeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
        val isoRange = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )

        // 手动快门
        val isManualShutterSupported = aeModes.contains(CaptureRequest.CONTROL_AE_MODE_OFF)
        val shutterSpeedRange = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )

        // 白平衡
        val awbModes = characteristics.get(
            CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
        )
        val whiteBalanceTemperatureRange = try {
            // 部分设备支持通过 COLOR_CORRECTION_GAINS 控制色温
            // 标准范围 2300K - 10000K
            Range(2300, 10000)
        } catch (e: Exception) {
            null
        }

        // 曝光补偿
        val exposureCompensationRange = characteristics.get(
            CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
        )
        val exposureCompensationStep = characteristics.get(
            CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP
        )?.toFloat()

        // 闪光灯
        val flashModes = characteristics.get(
            CameraCharacteristics.FLASH_INFO_AVAILABLE
        )

        // 变焦
        val maxZoom = characteristics.get(
            CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
        ) ?: 1.0f
        val isZoomSupported = maxZoom > 1.0f
        val focalLengths = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )

        // 多摄
        val isLogicalCamera = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
        )
        val physicalIds = if (isLogicalCamera) {
            characteristics.get(
                CameraCharacteristics.LOGICAL_MULTI_CAMERA_PHYSICAL_IDS
            )?.toList()
        } else null

        // 检测超广角/长焦/微距
        val ultraWideAvailable = detectUltraWide(focalLengths)
        val telephotoAvailable = detectTelephoto(focalLengths)
        val macroAvailable = detectMacro(focalLengths)

        // 帧率
        val fpsRanges = characteristics.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        ) ?: arrayOf(Range(15, 30))
        val maxCaptureFps = fpsRanges.maxOfOrNull { it.upper } ?: 30
        val maxVideoFps = getMaxVideoFps(fpsRanges)

        // 防抖
        val oisModes = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) ?: intArrayOf()
        val isOisSupported = oisModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        val eisModes = characteristics.get(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        ) ?: intArrayOf()
        val isEisSupported = eisModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        val stabilizationModes = characteristics.get(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )

        // 视频尺寸
        val supportedVideoSizes = getSupportedVideoSizes(streamMap)

        // 视频编码器
        val supportedVideoCodecs = getSupportedVideoCodecs()

        // 传感器
        val sensorOrientation = characteristics.get(
            CameraCharacteristics.SENSOR_ORIENTATION
        ) ?: 0
        val sensorSize = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
        )
        val pixelArraySize = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE
        )

        // 其他
        val noiseReductionModes = characteristics.get(
            CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
        )
        val edgeModes = characteristics.get(
            CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES
        )
        val colorEffects = characteristics.get(
            CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS
        )
        val maxRegions = characteristics.get(
            CameraCharacteristics.CONTROL_MAX_REGIONS_AF
        ) ?: 0

        return CameraCapabilities(
            cameraId = cameraId,
            hardwareLevel = hardwareLevel,
            facing = facing,
            isRawSupported = isRawSupported,
            rawSize = rawSize,
            isManualFocusSupported = isManualFocusSupported,
            minFocusDistance = minFocusDistance,
            isManualIsoSupported = isManualIsoSupported,
            isoRange = isoRange,
            isManualShutterSupported = isManualShutterSupported,
            shutterSpeedRange = shutterSpeedRange,
            whiteBalanceModes = awbModes,
            whiteBalanceTemperatureRange = whiteBalanceTemperatureRange,
            isExposureCompensationSupported = exposureCompensationRange != null,
            exposureCompensationRange = exposureCompensationRange,
            exposureCompensationStep = exposureCompensationStep,
            flashModes = flashModes,
            isZoomSupported = isZoomSupported,
            maxZoomRatio = maxZoom,
            availableFocalLengths = focalLengths,
            isLogicalCamera = isLogicalCamera,
            physicalCameraIds = physicalIds,
            isUltraWideAvailable = ultraWideAvailable,
            isTelephotoAvailable = telephotoAvailable,
            isMacroAvailable = macroAvailable,
            supportedFpsRanges = fpsRanges,
            maxCaptureFps = maxCaptureFps,
            maxVideoFps = maxVideoFps,
            isOisSupported = isOisSupported,
            isEisSupported = isEisSupported,
            stabilizationModes = stabilizationModes,
            supportedVideoSizes = supportedVideoSizes,
            supportedVideoCodecs = supportedVideoCodecs,
            sensorOrientation = sensorOrientation,
            sensorSize = sensorSize?.let { Size(it.width.toInt(), it.height.toInt()) },
            pixelArraySize = pixelArraySize,
            supportedNoiseReductionModes = noiseReductionModes,
            supportedEdgeModes = edgeModes,
            availableColorEffects = colorEffects,
            maxRegions = maxRegions
        )
    }

    private fun detectUltraWide(focalLengths: FloatArray?): Boolean {
        if (focalLengths == null) return false
        // 超广角通常焦距 < 20mm (等效)
        return focalLengths.any { it < 2.0f }
    }

    private fun detectTelephoto(focalLengths: FloatArray?): Boolean {
        if (focalLengths == null) return false
        // 长焦通常焦距 > 5.0mm (等效)
        return focalLengths.any { it > 5.0f }
    }

    private fun detectMacro(focalLengths: FloatArray?): Boolean {
        if (focalLengths == null) return false
        // 微距通常有非常短的焦距
        return focalLengths.any { it < 1.5f }
    }

    private fun getMaxVideoFps(ranges: Array<Range<Int>>): Int {
        return ranges.maxOfOrNull { it.upper } ?: 30
    }

    private fun getSupportedVideoSizes(streamMap: StreamConfigurationMap?): List<Size> {
        if (streamMap == null) return emptyList()
        return try {
            // 使用 YUV_420_888 作为通用视频格式
            streamMap.getOutputSizes(ImageFormat.YUV_420_888)?.toList()?.sortedByDescending {
                it.width * it.height
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getSupportedVideoCodecs(): List<Int> {
        val codecs = mutableListOf<Int>()
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue
            val name = codecInfo.name.lowercase()
            when {
                name.contains("h264") || name.contains("avc") ->
                    codecs.add(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                name.contains("h265") || name.contains("hevc") ->
                    codecs.add(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
        }
        return codecs.distinct()
    }
}
