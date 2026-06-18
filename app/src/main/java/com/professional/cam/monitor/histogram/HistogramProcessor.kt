package com.professional.cam.monitor.histogram

import android.graphics.ImageFormat
import android.media.Image
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.camera.analysis.Frame
import com.professional.cam.camera.analysis.FrameAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * 直方图处理器
 *
 * 从 YUV 帧计算 RGB 和亮度直方图。
 * 每个通道 256 级（0-255）。
 *
 * 采样控制：每 3 帧处理一次以降低 CPU 负载。
 */
class HistogramProcessor @Inject constructor() : FrameAnalyzer {

    override val name: String = "Histogram"

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    override val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    // 直方图数据（256级）
    private val redHist = IntArray(256)
    private val greenHist = IntArray(256)
    private val blueHist = IntArray(256)
    private val luminanceHist = IntArray(256)

    // 帧计数器
    private var frameCount = 0

    override fun shouldProcess(frame: Frame): Boolean {
        // 每 3 帧处理一次
        return frame.isYuv && ++frameCount % 3 == 0
    }

    override suspend fun analyze(frame: Frame): AnalysisResult {
        val image = frame.image

        return if (image.format == ImageFormat.YUV_420_888) {
            computeHistogram(image)
        } else {
            _result.value = null
            AnalysisResult.HistogramData(
                red = IntArray(256),
                green = IntArray(256),
                blue = IntArray(256),
                luminance = IntArray(256)
            )
        }
    }

    private fun computeHistogram(image: Image): AnalysisResult.HistogramData {
        // 重置直方图
        redHist.fill(0)
        greenHist.fill(0)
        blueHist.fill(0)
        luminanceHist.fill(0)

        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yPixelStride = yPlane.pixelStride
        val yRowStride = yPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val vRowStride = vPlane.rowStride

        val width = image.width
        val height = image.height

        // 采样步长（处理所有像素太慢，按比例采样）
        val sampleStep = when {
            width * height > 1920 * 1080 -> 4
            width * height > 1280 * 720 -> 2
            else -> 1
        }

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                // Y 分量
                val yIndex = y * yRowStride + x * yPixelStride
                val yValue = yBuffer.get(yIndex).toInt() and 0xFF

                // UV 分量（YUV420 半分辨率）
                val uIndex = (y / 2) * uRowStride + (x / 2) * uPixelStride
                val vIndex = (y / 2) * vRowStride + (x / 2) * vPixelStride
                val uValue = uBuffer.get(uIndex).toInt() and 0xFF
                val vValue = vBuffer.get(vIndex).toInt() and 0xFF

                // YUV -> RGB 转换（BT.601）
                val r = (yValue + 1.402f * (vValue - 128)).toInt().coerceIn(0, 255)
                val g = (yValue - 0.344f * (uValue - 128) - 0.714f * (vValue - 128)).toInt().coerceIn(0, 255)
                val b = (yValue + 1.772f * (uValue - 128)).toInt().coerceIn(0, 255)

                redHist[r]++
                greenHist[g]++
                blueHist[b]++
                luminanceHist[yValue]++
            }
        }

        val result = AnalysisResult.HistogramData(
            red = redHist.copyOf(),
            green = greenHist.copyOf(),
            blue = blueHist.copyOf(),
            luminance = luminanceHist.copyOf()
        )

        _result.value = result
        return result
    }
}
