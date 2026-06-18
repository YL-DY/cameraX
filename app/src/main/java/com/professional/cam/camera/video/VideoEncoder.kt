package com.professional.cam.camera.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.Surface
import com.professional.cam.core.util.Logger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * 视频编码器
 *
 * 使用 MediaCodec 进行 H.264/H.265 硬件编码。
 * 职责：
 * - 创建并配置 MediaCodec
 * - 管理输入 Surface（接收 Camera2 预览帧）
 * - 输出编码后的数据到回调
 *
 * 设计原则：
 * - 所有操作在独立线程执行
 * - 支持动态码率调整
 * - 支持 H.264 和 H.265
 * - 错误时自动降级（H.265 -> H.264）
 */
class VideoEncoder @Inject constructor() {

    companion object {
        private const val KEY_FRAME_RATE = 30
        private const val KEY_I_FRAME_INTERVAL = 1 // 关键帧间隔（秒）
        private const val BITRATE_1080P = 20_000_000 // 20 Mbps
        private const val BITRATE_4K = 50_000_000 // 50 Mbps
        private const val BITRATE_720P = 10_000_000 // 10 Mbps
        private const val TIMEOUT_US = 10_000L // 10ms
    }

    // 编码器状态
    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var isRunning = AtomicBoolean(false)
    private var outputFormat: MediaFormat? = null

    // 编码器配置
    private var width: Int = 1920
    private var height: Int = 1080
    private var bitrate: Int = BITRATE_1080P
    private var frameRate: Int = KEY_FRAME_RATE
    private var codecType: String = MediaFormat.MIMETYPE_VIDEO_AVC // H.264

    // 编码输出回调
    private var onEncodedData: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    private var onFormatChanged: ((MediaFormat) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    // 编码线程
    private var encoderThread: Thread? = null

    /**
     * 配置编码器参数
     */
    fun configure(
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Int = 30,
        bitrate: Int? = null,
        useHevc: Boolean = false
    ) {
        this.width = width
        this.height = height
        this.frameRate = frameRate
        this.bitrate = bitrate ?: calculateBitrate(width, height)
        this.codecType = if (useHevc && isHevcSupported()) {
            MediaFormat.MIMETYPE_VIDEO_HEVC
        } else {
            MediaFormat.MIMETYPE_VIDEO_AVC
        }
    }

    /**
     * 设置编码数据回调
     */
    fun setOnEncodedDataCallback(callback: (ByteBuffer, MediaCodec.BufferInfo) -> Unit) {
        onEncodedData = callback
    }

    /**
     * 设置格式变化回调
     */
    fun setOnFormatChangedCallback(callback: (MediaFormat) -> Unit) {
        onFormatChanged = callback
    }

    /**
     * 设置错误回调
     */
    fun setOnErrorCallback(callback: (String) -> Unit) {
        onError = callback
    }

    /**
     * 启动编码器
     *
     * @return 输入 Surface，Camera2 将帧渲染到此 Surface
     */
    fun start(): Surface? {
        if (isRunning.get()) {
            Logger.w(Logger.Tag.RECORDING, "VideoEncoder already running")
            return inputSurface
        }

        try {
            val mimeType = codecType
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_I_FRAME_INTERVAL)

                // Android 10+ 支持低延迟编码
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_LATENCY, 0)
                }
            }

            val codec = MediaCodec.createEncoderByType(mimeType)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
            mediaCodec = codec
            isRunning.set(true)

            // 启动输出处理线程
            startOutputThread()

            Logger.d(Logger.Tag.RECORDING,
                "VideoEncoder started: ${width}x$height @ ${frameRate}fps, bitrate=$bitrate, codec=$mimeType")

            return inputSurface
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to start VideoEncoder")
            onError?.invoke("Video encoder start failed: ${e.message}")
            return null
        }
    }

    /**
     * 停止编码器
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            encoderThread?.join(2000)
            encoderThread = null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error stopping VideoEncoder")
        }

        mediaCodec = null
        inputSurface = null
        outputFormat = null

        Logger.d(Logger.Tag.RECORDING, "VideoEncoder stopped")
    }

    /**
     * 获取输入 Surface（Camera2 渲染目标）
     */
    fun getInputSurface(): Surface? = inputSurface

    /**
     * 获取输出格式（用于初始化 MediaMuxer）
     */
    fun getOutputFormat(): MediaFormat? = outputFormat

    /**
     * 动态调整码率
     */
    fun adjustBitrate(newBitrate: Int) {
        bitrate = newBitrate
        try {
            val codec = mediaCodec
            if (codec != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val params = Bundle().apply {
                    putInt(MediaFormat.KEY_BIT_RATE, newBitrate)
                }
                codec.setParameters(params)
                Logger.d(Logger.Tag.RECORDING, "Bitrate adjusted to: $newBitrate")
            }
        } catch (e: Exception) {
            Logger.w(Logger.Tag.RECORDING, "Bitrate adjustment not supported on this device")
        }
    }

    /**
     * 是否正在运行
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * 释放资源
     */
    fun release() {
        stop()
        onEncodedData = null
        onFormatChanged = null
        onError = null
    }

    // ── 私有方法 ──

    private fun startOutputThread() {
        encoderThread = Thread({
            val codec = mediaCodec ?: return@Thread
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRunning.get()) {
                try {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = codec.outputFormat
                            onFormatChanged?.invoke(codec.outputFormat)
                            Logger.d(Logger.Tag.RECORDING, "Video encoder output format changed")
                        }

                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // 正常超时，继续循环
                        }

                        outputIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                onEncodedData?.invoke(outputBuffer, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Logger.e(Logger.Tag.RECORDING, e, "Video encoder output error")
                        onError?.invoke("Video encoder error: ${e.message}")
                    }
                }
            }
        }, "VideoEncoder-Output").apply { start() }
    }

    private fun calculateBitrate(width: Int, height: Int): Int {
        val pixels = width * height
        return when {
            pixels >= 3840 * 2160 -> BITRATE_4K
            pixels >= 1920 * 1080 -> BITRATE_1080P
            else -> BITRATE_720P
        }
    }

    private fun isHevcSupported(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.findEncoderForFormat(
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
            ) != null
        } catch (e: Exception) {
            false
        }
    }
}
