package com.professional.cam.camera.analysis

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.util.Size
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 图像引擎
 *
 * 独立于相机管线的图像处理引擎。
 * 职责：
 * 1. 管理 ImageReader（拍照 + 帧分析）
 * 2. 分发帧到所有注册的 FrameAnalyzer
 * 3. 处理拍照结果
 *
 * Image Pipeline 与 Camera Pipeline 完全解耦。
 * 未来可添加 HDR/RAW/AI 等分析器，只需实现 FrameAnalyzer 接口并注册。
 */
class ImageEngine @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 注册的帧分析器
    private val analyzers = mutableListOf<FrameAnalyzer>()

    // 帧计数器（用于采样控制）
    private var frameCount = 0

    // 分析用 ImageReader
    private var analysisReader: ImageReader? = null

    // 拍照用 ImageReader
    private var captureReader: ImageReader? = null

    /**
     * 创建分析用 ImageReader
     */
    fun createAnalysisReader(
        width: Int = 640,
        height: Int = 480,
        maxImages: Int = 5,
        onImageAvailable: (ImageReader) -> Unit
    ): ImageReader {
        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, maxImages)
        reader.setOnImageAvailableListener({ onImageAvailable(it) }, null)
        analysisReader = reader
        return reader
    }

    /**
     * 创建拍照用 ImageReader
     */
    fun createCaptureReader(
        width: Int = 1920,
        height: Int = 1080,
        format: Int = ImageFormat.JPEG,
        maxImages: Int = 2,
        onImageAvailable: (ImageReader) -> Unit
    ): ImageReader {
        val reader = ImageReader.newInstance(width, height, format, maxImages)
        reader.setOnImageAvailableListener({ onImageAvailable(it) }, null)
        captureReader = reader
        return reader
    }

    /**
     * 注册帧分析器
     */
    fun registerAnalyzer(analyzer: FrameAnalyzer) {
        if (analyzers.none { it.name == analyzer.name }) {
            analyzers.add(analyzer)
            Logger.d(Logger.Tag.MONITOR, "Analyzer registered: ${analyzer.name}")
        }
    }

    /**
     * 注销帧分析器
     */
    fun unregisterAnalyzer(analyzer: FrameAnalyzer) {
        analyzers.removeAll { it.name == analyzer.name }
        Logger.d(Logger.Tag.MONITOR, "Analyzer unregistered: ${analyzer.name}")
    }

    /**
     * 清除所有分析器
     */
    fun clearAnalyzers() {
        analyzers.clear()
    }

    /**
     * 分发帧到所有注册的分析器
     *
     * 在独立协程中并行处理，互不阻塞。
     * 帧采样控制：根据 analyzers 的 shouldProcess 决定是否处理。
     */
    fun dispatchFrame(image: Image) {
        if (analyzers.isEmpty()) {
            image.close()
            return
        }

        frameCount++
        val frame = Frame(
            image = image,
            timestamp = System.nanoTime(),
            format = image.format,
            size = Size(image.width, image.height),
            rotation = 0
        )

        // 并行分发到所有分析器
        analyzers.forEach { analyzer ->
            if (analyzer.shouldProcess(frame)) {
                scope.launch {
                    try {
                        val result = analyzer.analyze(frame)
                        // result 通过 analyzer.result StateFlow 自动更新
                    } catch (e: Exception) {
                        Logger.e(Logger.Tag.MONITOR, e, "Analyzer error: ${analyzer.name}")
                    }
                }
            }
        }

        // 关闭 Image（重要：必须关闭，否则会泄漏）
        image.close()
    }

    /**
     * 处理拍照结果
     */
    fun processCapture(image: Image, mode: CaptureMode): CaptureResult {
        return when (mode) {
            CaptureMode.JPEG -> processJpegCapture(image)
            CaptureMode.RAW -> processRawCapture(image)
            CaptureMode.JPEG_RAW -> processJpegRawCapture(image)
        }
    }

    private fun processJpegCapture(image: Image): CaptureResult {
        // TODO: JPEG 拍照处理
        image.close()
        return CaptureResult.Success(byteArrayOf())
    }

    private fun processRawCapture(image: Image): CaptureResult {
        // TODO: RAW 拍照处理
        image.close()
        return CaptureResult.Success(byteArrayOf())
    }

    private fun processJpegRawCapture(image: Image): CaptureResult {
        // TODO: JPEG+RAW 双流拍照处理
        image.close()
        return CaptureResult.Success(byteArrayOf())
    }

    /**
     * 释放资源
     */
    fun release() {
        analysisReader?.close()
        analysisReader = null
        captureReader?.close()
        captureReader = null
        analyzers.clear()
    }
}

/**
 * 拍照模式
 */
enum class CaptureMode {
    JPEG,
    RAW,
    JPEG_RAW
}

/**
 * 拍照结果
 */
sealed class CaptureResult {
    data class Success(val data: ByteArray) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
}
