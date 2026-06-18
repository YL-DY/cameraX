package com.professional.cam.camera.analysis

import android.graphics.ImageFormat
import android.media.Image
import android.util.Size
import kotlinx.coroutines.flow.StateFlow

/**
 * 帧分析器接口
 *
 * 所有图像分析器的统一接口。
 * Image Pipeline 与 Camera Pipeline 完全解耦，通过此接口连接。
 *
 * 预留扩展点：
 * - HistogramProcessor
 * - WaveformProcessor
 * - ZebraProcessor
 * - FocusPeakingProcessor
 * - LutProcessor
 * - HdrProcessor (Future)
 * - RawProcessor (Future)
 * - AiProcessor (Future)
 */
interface FrameAnalyzer {
    /** 分析器名称 */
    val name: String

    /** 是否需要处理此帧（采样控制） */
    fun shouldProcess(frame: Frame): Boolean

    /** 处理帧数据 */
    suspend fun analyze(frame: Frame): AnalysisResult

    /** 处理结果回调 */
    val result: StateFlow<AnalysisResult?>
}

/**
 * 帧数据封装
 */
data class Frame(
    val image: Image,
    val timestamp: Long,
    val format: Int,
    val size: Size,
    val rotation: Int
) {
    val isYuv: Boolean get() = format == ImageFormat.YUV_420_888
    val isJpeg: Boolean get() = format == ImageFormat.JPEG
    val isRaw: Boolean get() = format == ImageFormat.RAW_SENSOR
}

/**
 * 分析结果基类
 */
sealed class AnalysisResult {
    data class HistogramData(
        val red: IntArray,
        val green: IntArray,
        val blue: IntArray,
        val luminance: IntArray
    ) : AnalysisResult()

    data class WaveformData(
        val luminance: FloatArray,
        val width: Int,
        val height: Int
    ) : AnalysisResult()

    data class ZebraData(
        val overexposedRegions: List<Region>,
        val threshold: Int
    ) : AnalysisResult()

    data class PeakingData(
        val edgePixels: List<Region>,
        val intensity: Float
    ) : AnalysisResult()

    data class LutData(
        val rgb: IntArray,
        val width: Int,
        val height: Int
    ) : AnalysisResult()

    // 预留扩展
    // data class HdrData(...) : AnalysisResult()
    // data class RawData(...) : AnalysisResult()
    // data class AiData(...) : AnalysisResult()
}

/**
 * 区域数据
 */
data class Region(
    val x: Int,
    val y: Int,
    val width: Int = 1,
    val height: Int = 1
)
