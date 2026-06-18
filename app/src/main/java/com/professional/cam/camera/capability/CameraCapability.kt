package com.professional.cam.camera.capability

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range

/**
 * 统一相机能力模型
 *
 * 通过 [CameraCharacteristics] 自动读取设备能力，不允许写死任何值。
 * 所有手动控制功能基于此模型做显示/隐藏/降级决策。
 *
 * 设计原则：
 * - 所有字段通过 [CameraCharacteristics] 自动检测
 * - 不包含品牌或设备型号硬编码
 * - 不可变数据类，创建后不可修改
 *
 * @property supportsManualSensor 是否支持手动传感器控制（ISO + 快门）
 * @property supportsManualPostProcessing 是否支持手动后处理（白平衡等）
 * @property supportsRaw 是否支持 RAW 输出
 * @property supportsFlash 是否支持闪光灯
 * @property supportsOpticalStabilization 是否支持光学防抖（OIS）
 * @property isoRange ISO 范围（含最小值/最大值）
 * @property exposureTimeRange 曝光时间范围（纳秒）
 * @property focusDistanceRange 对焦距离范围（米）
 * @property maxZoomRatio 最大变焦倍率
 * @property whiteBalanceModes 支持的白平衡模式列表
 */
data class CameraCapability(
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRaw: Boolean,
    val supportsFlash: Boolean,
    val supportsOpticalStabilization: Boolean,
    val isoRange: Range<Int>,
    val exposureTimeRange: Range<Long>,
    val focusDistanceRange: Range<Float>,
    val maxZoomRatio: Float,
    val whiteBalanceModes: List<Int>
) {
    companion object {

        /**
         * 从 [CameraCharacteristics] 构建 [CameraCapability]
         *
         * @param characteristics 相机特征信息
         * @return 解析后的能力模型
         */
        fun fromCharacteristics(characteristics: CameraCharacteristics): CameraCapability {
            // ── 手动传感器控制检测 ──
            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            ) ?: intArrayOf()

            val supportsManualSensor = capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            )

            val supportsManualPostProcessing = capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
            )

            val supportsRaw = capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW
            )

            // ── 闪光灯 ──
            val flashAvailable = characteristics.get(
                CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) ?: false

            // ── 防抖 ──
            val stabilizationModes = characteristics.get(
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
            ) ?: intArrayOf()
            val supportsVideoStabilization = stabilizationModes.contains(
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
            // LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION 返回 Boolean?，表示是否支持 OIS
            @Suppress("UNCHECKED_CAST")
            val oisSupported = (characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
            ) as? Boolean) ?: false
            val supportsOis = oisSupported || supportsVideoStabilization

            // ── ISO 范围 ──
            val isoRange = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            ) ?: Range(100, 800)

            // ── 曝光时间范围 ──
            val exposureTimeRange = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            ) ?: Range(100000L, 30000000L)

            // ── 对焦距离范围 ──
            val minFocusDistance = characteristics.get(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
            ) ?: 0.0f
            val focusDistanceRange = if (minFocusDistance > 0f) {
                Range(0f, minFocusDistance)
            } else {
                Range(0f, 1f)
            }

            // ── 变焦 ──
            val maxZoom = characteristics.get(
                CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
            ) ?: 1.0f

            // ── 白平衡模式 ──
            val availableWbModes = characteristics.get(
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
            ) ?: intArrayOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
            val wbModes = availableWbModes.toList()

            return CameraCapability(
                supportsManualSensor = supportsManualSensor,
                supportsManualPostProcessing = supportsManualPostProcessing,
                supportsRaw = supportsRaw,
                supportsFlash = flashAvailable,
                supportsOpticalStabilization = supportsOis,
                isoRange = isoRange,
                exposureTimeRange = exposureTimeRange,
                focusDistanceRange = focusDistanceRange,
                maxZoomRatio = maxZoom,
                whiteBalanceModes = wbModes
            )
        }
    }
}
