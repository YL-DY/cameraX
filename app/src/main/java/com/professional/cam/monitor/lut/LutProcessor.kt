package com.professional.cam.monitor.lut

import android.graphics.Bitmap
import android.graphics.Color
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
 * LUT（查找表）预览处理器
 *
 * 将 3D LUT 应用于预览帧，实现实时色彩分级预览。
 * 支持 .cube 格式的 LUT 文件。
 *
 * 注意：这是一个简化实现，实际生产环境应使用 GPU 加速（OpenGL/Vulkan）。
 * 当前实现使用 CPU 逐像素处理，仅适用于低分辨率预览。
 *
 * 采样控制：每 6 帧处理一次（CPU 实现性能有限）。
 */
class LutProcessor @Inject constructor() : FrameAnalyzer {

    override val name: String = "LUT"

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    override val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    // LUT 数据
    private var lutData: Lut3D? = null
    private var isEnabled: Boolean = false

    // 帧计数器
    private var frameCount = 0

    override fun shouldProcess(frame: Frame): Boolean {
        return isEnabled && lutData != null && frame.isYuv && ++frameCount % 6 == 0
    }

    override suspend fun analyze(frame: Frame): AnalysisResult {
        val image = frame.image

        return if (image.format == ImageFormat.YUV_420_888 && lutData != null) {
            applyLut(image)
        } else {
            _result.value = null
            AnalysisResult.LutData(
                rgb = IntArray(0),
                width = 0,
                height = 0
            )
        }
    }

    /**
     * 加载 .cube LUT 文件
     *
     * @param cubeData .cube 格式的 LUT 数据
     * @param size LUT 大小（通常 16、32、64）
     */
    fun loadLut(cubeData: ByteArray, size: Int = 32) {
        try {
            val lut = parseCubeData(cubeData, size)
            if (lut != null) {
                lutData = lut
                Logger.d("LUT loaded: ${size}x${size}x${size}")
            }
        } catch (e: Exception) {
            Logger.e("Failed to load LUT: ${e.message}")
        }
    }

    /**
     * 启用/禁用 LUT
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * 是否已启用
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * 清除 LUT
     */
    fun clearLut() {
        lutData = null
        isEnabled = false
    }

    private fun applyLut(image: Image): AnalysisResult.LutData {
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

        // 降采样处理
        val scaleFactor = 4
        val outWidth = width / scaleFactor
        val outHeight = height / scaleFactor

        val rgb = IntArray(outWidth * outHeight)
        val lut = lutData ?: return AnalysisResult.LutData(rgb, outWidth, outHeight)

        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                val srcX = x * scaleFactor
                val srcY = y * scaleFactor

                // 读取 YUV
                val yIndex = srcY * yRowStride + srcX * yPixelStride
                val uIndex = (srcY / 2) * uRowStride + (srcX / 2) * uPixelStride
                val vIndex = (srcY / 2) * vRowStride + (srcX / 2) * vPixelStride

                val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                val uVal = uBuffer.get(uIndex).toInt() and 0xFF
                val vVal = vBuffer.get(vIndex).toInt() and 0xFF

                // YUV -> RGB
                val r = (yVal + 1.402f * (vVal - 128)).toInt().coerceIn(0, 255)
                val g = (yVal - 0.344f * (uVal - 128) - 0.714f * (vVal - 128)).toInt().coerceIn(0, 255)
                val b = (yVal + 1.772f * (uVal - 128)).toInt().coerceIn(0, 255)

                // 应用 LUT
                val lutColor = lut.apply(r, g, b)
                rgb[y * outWidth + x] = Color.rgb(lutColor[0], lutColor[1], lutColor[2])
            }
        }

        val result = AnalysisResult.LutData(
            rgb = rgb,
            width = outWidth,
            height = outHeight
        )

        _result.value = result
        return result
    }

    private fun parseCubeData(data: ByteArray, size: Int): Lut3D? {
        return try {
            val text = String(data)
            val lines = text.lines()
            val values = mutableListOf<Float>()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") ||
                    trimmed.startsWith("TITLE") || trimmed.startsWith("LUT_3D_SIZE") ||
                    trimmed.startsWith("DOMAIN_MIN") || trimmed.startsWith("DOMAIN_MAX")) {
                    continue
                }
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    parts.take(3).forEach { values.add(it.toFloat()) }
                }
            }

            if (values.size < size * size * size * 3) {
                Logger.e("Invalid LUT data: expected ${size * size * size * 3} values, got ${values.size}")
                return null
            }

            Lut3D(size, values.toFloatArray())
        } catch (e: Exception) {
            Logger.e("Failed to parse LUT: ${e.message}")
            null
        }
    }

    /**
     * 3D LUT 数据
     */
    private class Lut3D(
        val size: Int,
        val data: FloatArray
    ) {
        fun apply(r: Int, g: Int, b: Int): IntArray {
            // 归一化到 0-1
            val rn = r / 255f
            val gn = g / 255f
            val bn = b / 255f

            // 计算 LUT 索引
            val ri = (rn * (size - 1)).toInt().coerceIn(0, size - 1)
            val gi = (gn * (size - 1)).toInt().coerceIn(0, size - 1)
            val bi = (bn * (size - 1)).toInt().coerceIn(0, size - 1)

            val index = (bi * size * size + gi * size + ri) * 3

            return if (index + 2 < data.size) {
                intArrayOf(
                    (data[index] * 255).toInt().coerceIn(0, 255),
                    (data[index + 1] * 255).toInt().coerceIn(0, 255),
                    (data[index + 2] * 255).toInt().coerceIn(0, 255)
                )
            } else {
                intArrayOf(r, g, b)
            }
        }
    }

    // 简单日志（避免依赖 Logger 导致循环引用）
    private object Logger {
        fun d(msg: String) = android.util.Log.d("LutProcessor", msg)
        fun e(msg: String) = android.util.Log.e("LutProcessor", msg)
    }
}
