package com.professional.cam.monitor.zebra

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
 * 斑马纹处理器
 *
 * 检测图像中过曝区域，用于辅助曝光判断。
 * 在亮度超过阈值的区域标记斑马纹。
 *
 * 默认阈值：95%（可配置）
 * 采样控制：每 2 帧处理一次。
 */
class ZebraProcessor @Inject constructor() : FrameAnalyzer {

    override val name: String = "Zebra"

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    override val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    // 斑马纹阈值（百分比 0-100）
    private var threshold: Int = 95

    // 帧计数器
    private var frameCount = 0

    override fun shouldProcess(frame: Frame): Boolean {
        return frame.isYuv && ++frameCount % 2 == 0
    }

    override suspend fun analyze(frame: Frame): AnalysisResult {
        val image = frame.image

        return if (image.format == ImageFormat.YUV_420_888) {
            detectOverexposed(image)
        } else {
            _result.value = null
            AnalysisResult.ZebraData(
                overexposedRegions = emptyList(),
                threshold = threshold
            )
        }
    }

    /**
     * 设置斑马纹阈值
     */
    fun setThreshold(percent: Int) {
        threshold = percent.coerceIn(50, 100)
    }

    /**
     * 获取当前阈值
     */
    fun getThreshold(): Int = threshold

    private fun detectOverexposed(image: Image): AnalysisResult.ZebraData {
        val planes = image.planes
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yPixelStride = yPlane.pixelStride
        val yRowStride = yPlane.rowStride

        val width = image.width
        val height = image.height

        // 亮度阈值（0-255）
        val luminanceThreshold = (threshold / 100f * 255).toInt()

        // 过曝区域列表
        val regions = mutableListOf<Region>()

        // 采样步长
        val sampleStep = when {
            width * height > 1920 * 1080 -> 8
            width * height > 1280 * 720 -> 4
            else -> 2
        }

        // 检测过曝像素并合并为区域
        var regionStartX = -1
        var regionStartY = -1

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val yIndex = y * yRowStride + x * yPixelStride
                val yValue = yBuffer.get(yIndex).toInt() and 0xFF

                if (yValue >= luminanceThreshold) {
                    if (regionStartX == -1) {
                        regionStartX = x
                        regionStartY = y
                    }
                } else {
                    if (regionStartX != -1) {
                        regions.add(Region(
                            x = regionStartX,
                            y = regionStartY,
                            width = x - regionStartX,
                            height = sampleStep
                        ))
                        regionStartX = -1
                    }
                }
            }
            if (regionStartX != -1) {
                regions.add(Region(
                    x = regionStartX,
                    y = regionStartY,
                    width = width - regionStartX,
                    height = sampleStep
                ))
                regionStartX = -1
            }
        }

        // 限制区域数量
        val limitedRegions = if (regions.size > 500) {
            regions.sortedByDescending { it.width * it.height }.take(500)
        } else {
            regions
        }

        val result = AnalysisResult.ZebraData(
            overexposedRegions = limitedRegions,
            threshold = threshold
        )

        _result.value = result
        return result
    }
}
