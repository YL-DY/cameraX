package com.professional.cam.monitor.waveform

import android.graphics.ImageFormat
import android.media.Image
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.camera.analysis.Frame
import com.professional.cam.camera.analysis.FrameAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 波形监视器处理器
 *
 * 从 YUV 帧计算亮度波形数据。
 * 波形图显示每一列的亮度分布，用于精确曝光判断。
 *
 * 采样控制：每 3 帧处理一次。
 */
class WaveformProcessor @Inject constructor() : FrameAnalyzer {

    override val name: String = "Waveform"

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    override val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    // 帧计数器
    private var frameCount = 0

    override fun shouldProcess(frame: Frame): Boolean {
        return frame.isYuv && ++frameCount % 3 == 0
    }

    override suspend fun analyze(frame: Frame): AnalysisResult {
        val image = frame.image

        return if (image.format == ImageFormat.YUV_420_888) {
            computeWaveform(image)
        } else {
            _result.value = null
            AnalysisResult.WaveformData(
                luminance = FloatArray(0),
                width = 0,
                height = 0
            )
        }
    }

    private fun computeWaveform(image: Image): AnalysisResult.WaveformData {
        val planes = image.planes
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yPixelStride = yPlane.pixelStride
        val yRowStride = yPlane.rowStride

        val width = image.width
        val height = image.height

        // 波形图宽度 = 原始宽度（按比例缩小）
        val waveformWidth = width.coerceAtMost(640)
        val columnStep = width / waveformWidth

        // 波形图高度 = 256（亮度级）
        val waveformHeight = 256

        // 存储每列的最大亮度值
        val luminance = FloatArray(waveformWidth * waveformHeight)

        // 采样步长（垂直方向）
        val sampleStep = when {
            height > 1080 -> 4
            height > 720 -> 2
            else -> 1
        }

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width) {
                val yIndex = y * yRowStride + x * yPixelStride
                val yValue = yBuffer.get(yIndex).toInt() and 0xFF

                val col = x / columnStep
                if (col < waveformWidth) {
                    val lumIndex = yValue * waveformWidth + col
                    luminance[lumIndex] = 1.0f
                }
            }
        }

        val result = AnalysisResult.WaveformData(
            luminance = luminance,
            width = waveformWidth,
            height = waveformHeight
        )

        _result.value = result
        return result
    }
}
