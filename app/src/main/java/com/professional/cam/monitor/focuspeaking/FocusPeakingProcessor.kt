package com.professional.cam.monitor.focuspeaking

import android.graphics.ImageFormat
import android.media.Image
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.camera.analysis.Frame
import com.professional.cam.camera.analysis.FrameAnalyzer
import com.professional.cam.camera.analysis.Region
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 峰值对焦处理器
 *
 * 检测图像中边缘清晰度最高的区域，用于辅助手动对焦。
 * 使用 Sobel 算子检测边缘，高对比度区域标记为合焦。
 *
 * 采样控制：每 2 帧处理一次。
 * 性能优化：降采样处理，仅处理 Y 通道。
 */
class FocusPeakingProcessor @Inject constructor() : FrameAnalyzer {

    override val name: String = "FocusPeaking"

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    override val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    // 峰值检测灵敏度
    private var intensity: Float = 0.5f

    // 帧计数器
    private var frameCount = 0

    override fun shouldProcess(frame: Frame): Boolean {
        return frame.isYuv && ++frameCount % 2 == 0
    }

    override suspend fun analyze(frame: Frame): AnalysisResult {
        val image = frame.image

        return if (image.format == ImageFormat.YUV_420_888) {
            detectEdges(image)
        } else {
            _result.value = null
            AnalysisResult.PeakingData(
                edgePixels = emptyList(),
                intensity = intensity
            )
        }
    }

    /**
     * 设置峰值检测灵敏度
     */
    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0.1f, 1.0f)
    }

    /**
     * 获取当前灵敏度
     */
    fun getIntensity(): Float = intensity

    private fun detectEdges(image: Image): AnalysisResult.PeakingData {
        val planes = image.planes
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yPixelStride = yPlane.pixelStride
        val yRowStride = yPlane.rowStride

        val width = image.width
        val height = image.height

        // 降采样因子
        val scaleFactor = when {
            width * height > 1920 * 1080 -> 4
            width * height > 1280 * 720 -> 2
            else -> 1
        }

        val scaledWidth = width / scaleFactor
        val scaledHeight = height / scaleFactor

        // 读取 Y 通道数据（降采样）
        val yData = IntArray(scaledWidth * scaledHeight)
        for (y in 0 until scaledHeight) {
            for (x in 0 until scaledWidth) {
                val srcX = x * scaleFactor
                val srcY = y * scaleFactor
                val yIndex = srcY * yRowStride + srcX * yPixelStride
                yData[y * scaledWidth + x] = yBuffer.get(yIndex).toInt() and 0xFF
            }
        }

        // Sobel 边缘检测
        val edgeThreshold = (intensity * 50).toInt()
        val edgePixels = mutableListOf<Region>()

        for (y in 1 until scaledHeight - 1) {
            for (x in 1 until scaledWidth - 1) {
                // Sobel X
                val gx = (-1 * yData[(y - 1) * scaledWidth + (x - 1)] +
                        1 * yData[(y - 1) * scaledWidth + (x + 1)] +
                        -2 * yData[y * scaledWidth + (x - 1)] +
                        2 * yData[y * scaledWidth + (x + 1)] +
                        -1 * yData[(y + 1) * scaledWidth + (x - 1)] +
                        1 * yData[(y + 1) * scaledWidth + (x + 1)])

                // Sobel Y
                val gy = (-1 * yData[(y - 1) * scaledWidth + (x - 1)] +
                        -2 * yData[(y - 1) * scaledWidth + x] +
                        -1 * yData[(y - 1) * scaledWidth + (x + 1)] +
                        1 * yData[(y + 1) * scaledWidth + (x - 1)] +
                        2 * yData[(y + 1) * scaledWidth + x] +
                        1 * yData[(y + 1) * scaledWidth + (x + 1)])

                val magnitude = kotlin.math.sqrt((gx * gx + gy * gy).toFloat()).toInt()

                if (magnitude > edgeThreshold) {
                    edgePixels.add(Region(
                        x = x * scaleFactor,
                        y = y * scaleFactor,
                        width = scaleFactor,
                        height = scaleFactor
                    ))
                }
            }
        }

        // 限制边缘像素数量
        val limitedEdges = if (edgePixels.size > 1000) {
            edgePixels.shuffled().take(1000)
        } else {
            edgePixels
        }

        val result = AnalysisResult.PeakingData(
            edgePixels = limitedEdges,
            intensity = intensity
        )

        _result.value = result
        return result
    }
}
