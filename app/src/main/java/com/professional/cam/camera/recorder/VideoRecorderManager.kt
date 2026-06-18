package com.professional.cam.camera.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.professional.cam.camera.audio.AudioRecorder
import com.professional.cam.camera.video.VideoEncoder
import com.professional.cam.core.util.Logger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频录制管理器
 *
 * 独立模块，负责管理完整的视频录制生命周期。
 * 职责：
 * - 创建并管理 [MediaMuxer]（MP4 复用器）
 * - 协调 [VideoEncoder] 和 [AudioRecorder] 的工作
 * - 管理录制状态机（Idle → Preparing → Recording → Stopping → Completed）
 * - 录制文件输出到指定路径
 * - 错误恢复（Error → Idle）
 *
 * 设计原则：
 * - 独立模块，不将录制逻辑写入 [com.professional.cam.camera.manager.Camera2Engine]
 * - 单次录制 = 一个 [VideoRecorderManager] 实例
 * - 支持无音频录制（音频不可用时自动降级）
 * - 所有操作线程安全
 */
@Singleton
class VideoRecorderManager @Inject constructor(
    private val videoEncoder: VideoEncoder,
    private val audioRecorder: AudioRecorder
) {

    companion object {
        private const val MAX_DURATION_MS = 30 * 60 * 1000L // 30 分钟
        private const val MAX_FILE_SIZE_BYTES = 4L * 1024 * 1024 * 1024 // 4GB
    }

    // ── 录制状态机 ──

    private val _state = AtomicReference(RecorderState.Idle)
    val state: RecorderState get() = _state.get()

    // ── 录制状态标志 ──

    private val _isRecording = AtomicBoolean(false)
    val isRecording: Boolean get() = _isRecording.get()

    // ── MediaMuxer ──

    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private val muxerStarted = AtomicBoolean(false)

    // ── 轨道就绪标志 ──

    private val videoTrackReady = AtomicBoolean(false)
    private val audioTrackReady = AtomicBoolean(false)

    // ── 时间戳偏移 ──

    private var videoBaseTimeUs: Long = -1
    private var audioBaseTimeUs: Long = -1

    // ── 录制配置 ──

    private var outputPath: String = ""
    private var startTimeMs: Long = 0
    private var enableAudio: Boolean = true
    private var hasAudio: Boolean = false
    private var maxDurationMs: Long = MAX_DURATION_MS
    private var maxFileSize: Long = MAX_FILE_SIZE_BYTES

    // ── 回调 ──

    private var onRecordingComplete: ((String) -> Unit)? = null
    private var onRecordingError: ((String) -> Unit)? = null
    private var onDurationUpdate: ((Long) -> Unit)? = null

    /**
     * 设置录制完成回调
     */
    fun setOnRecordingCompleteCallback(callback: (String) -> Unit) {
        onRecordingComplete = callback
    }

    /**
     * 设置录制错误回调
     */
    fun setOnRecordingErrorCallback(callback: (String) -> Unit) {
        onRecordingError = callback
    }

    /**
     * 设置时长更新回调
     */
    fun setOnDurationUpdateCallback(callback: (Long) -> Unit) {
        onDurationUpdate = callback
    }

    /**
     * 配置录制参数
     *
     * @param outputPath 输出文件路径
     * @param videoWidth 视频宽度
     * @param videoHeight 视频高度
     * @param frameRate 帧率
     * @param bitrate 视频码率（null 则自动计算）
     * @param useHevc 是否使用 H.265
     * @param enableAudio 是否启用音频
     * @param maxDurationMs 最大录制时长（毫秒）
     * @param maxFileSize 最大文件大小（字节）
     */
    fun configure(
        outputPath: String,
        videoWidth: Int = 1920,
        videoHeight: Int = 1080,
        frameRate: Int = 30,
        bitrate: Int? = null,
        useHevc: Boolean = false,
        enableAudio: Boolean = true,
        maxDurationMs: Long = MAX_DURATION_MS,
        maxFileSize: Long = MAX_FILE_SIZE_BYTES
    ) {
        this.outputPath = outputPath
        this.enableAudio = enableAudio
        this.maxDurationMs = maxDurationMs
        this.maxFileSize = maxFileSize

        // 配置视频编码器
        videoEncoder.configure(
            width = videoWidth,
            height = videoHeight,
            frameRate = frameRate,
            bitrate = bitrate,
            useHevc = useHevc
        )

        // 设置视频编码器回调
        videoEncoder.setOnEncodedDataCallback { byteBuffer, bufferInfo ->
            writeVideoSample(byteBuffer, bufferInfo)
        }
        videoEncoder.setOnFormatChangedCallback { format ->
            onVideoFormatChanged(format)
        }
        videoEncoder.setOnErrorCallback { error ->
            onRecordingError?.invoke("Video encoder: $error")
        }

        // 配置音频录制器
        if (enableAudio) {
            audioRecorder.setOnEncodedDataCallback { byteBuffer, bufferInfo ->
                writeAudioSample(byteBuffer, bufferInfo)
            }
            audioRecorder.setOnFormatChangedCallback { format ->
                onAudioFormatChanged(format)
            }
            audioRecorder.setOnErrorCallback { error ->
                Logger.w(Logger.Tag.RECORDING, "Audio error: $error, continuing without audio")
                hasAudio = false
            }
        }
    }

    /**
     * 开始录制
     *
     * 状态转换：Idle → Preparing → Recording
     *
     * @return 视频输入 Surface（Camera2 渲染目标），失败返回 null
     */
    fun start(): Surface? {
        if (_isRecording.get()) {
            Logger.w(Logger.Tag.RECORDING, "VideoRecorderManager already recording")
            return videoEncoder.getInputSurface()
        }

        // Idle → Preparing
        if (!_state.compareAndSet(RecorderState.Idle, RecorderState.Preparing)) {
            Logger.w(Logger.Tag.RECORDING, "Cannot start recording from state: ${_state.get()}")
            return null
        }

        try {
            // 初始化 MediaMuxer
            mediaMuxer = MediaMuxer(
                outputPath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // 重置状态
            videoTrackReady.set(false)
            audioTrackReady.set(false)
            muxerStarted.set(false)
            videoTrackIndex = -1
            audioTrackIndex = -1
            videoBaseTimeUs = -1
            audioBaseTimeUs = -1
            hasAudio = false

            // 启动视频编码器
            val surface = videoEncoder.start()
            if (surface == null) {
                onRecordingError?.invoke("Video encoder failed to start")
                cleanup()
                _state.set(RecorderState.Error)
                return null
            }

            // 启动音频录制器
            if (enableAudio) {
                hasAudio = audioRecorder.start()
            }

            _isRecording.set(true)
            startTimeMs = System.currentTimeMillis()

            // Preparing → Recording
            _state.set(RecorderState.Recording)

            Logger.d(Logger.Tag.RECORDING, "VideoRecorderManager started: $outputPath")
            return surface
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to start VideoRecorderManager")
            onRecordingError?.invoke("Recording start failed: ${e.message}")
            cleanup()
            _state.set(RecorderState.Error)
            return null
        }
    }

    /**
     * 停止录制
     *
     * 状态转换：Recording → Stopping → Completed
     */
    fun stop() {
        val currentState = _state.get()
        if (currentState != RecorderState.Recording && currentState != RecorderState.Preparing) {
            Logger.w(Logger.Tag.RECORDING, "Cannot stop recording from state: $currentState")
            return
        }

        // Recording/Preparing → Stopping
        _state.set(RecorderState.Stopping)
        _isRecording.set(false)

        Logger.d(Logger.Tag.RECORDING, "VideoRecorderManager stopping...")

        // 停止编码器
        videoEncoder.stop()
        if (hasAudio) {
            audioRecorder.stop()
        }

        // 停止并释放 MediaMuxer
        stopMuxer()

        cleanup()

        val duration = System.currentTimeMillis() - startTimeMs
        Logger.d(Logger.Tag.RECORDING, "VideoRecorderManager stopped. Duration: ${duration}ms")

        // Stopping → Completed
        _state.set(RecorderState.Completed)

        // 回调录制完成
        onRecordingComplete?.invoke(outputPath)
    }

    /**
     * 获取当前录制时长（毫秒）
     */
    fun getCurrentDurationMs(): Long {
        if (!_isRecording.get()) return 0
        return System.currentTimeMillis() - startTimeMs
    }

    /**
     * 获取输出文件路径
     */
    fun getOutputPath(): String = outputPath

    /**
     * 释放所有资源
     */
    fun release() {
        if (_isRecording.get()) {
            stop()
        }
        videoEncoder.release()
        audioRecorder.release()
        onRecordingComplete = null
        onRecordingError = null
        onDurationUpdate = null
        _state.set(RecorderState.Idle)
    }

    // ── 私有方法 ──

    private fun onVideoFormatChanged(format: MediaFormat) {
        val muxer = mediaMuxer ?: return
        videoTrackIndex = muxer.addTrack(format)
        videoTrackReady.set(true)
        tryStartMuxer()
    }

    private fun onAudioFormatChanged(format: MediaFormat) {
        if (!hasAudio) return
        val muxer = mediaMuxer ?: return
        audioTrackIndex = muxer.addTrack(format)
        audioTrackReady.set(true)
        tryStartMuxer()
    }

    private fun tryStartMuxer() {
        if (muxerStarted.get()) return

        val videoReady = videoTrackReady.get()
        val audioReady = !hasAudio || audioTrackReady.get()

        if (videoReady && audioReady) {
            try {
                mediaMuxer?.start()
                muxerStarted.set(true)
                Logger.d(Logger.Tag.RECORDING, "MediaMuxer started")
            } catch (e: Exception) {
                Logger.e(Logger.Tag.RECORDING, e, "Failed to start MediaMuxer")
                onRecordingError?.invoke("Muxer start failed: ${e.message}")
            }
        }
    }

    private fun writeVideoSample(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!muxerStarted.get() || videoTrackIndex < 0) return

        // 时间戳偏移
        if (videoBaseTimeUs < 0) {
            videoBaseTimeUs = bufferInfo.presentationTimeUs
        }
        bufferInfo.presentationTimeUs -= videoBaseTimeUs

        try {
            mediaMuxer?.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error writing video sample")
        }
    }

    private fun writeAudioSample(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!muxerStarted.get() || audioTrackIndex < 0 || !hasAudio) return

        // 时间戳偏移
        if (audioBaseTimeUs < 0) {
            audioBaseTimeUs = bufferInfo.presentationTimeUs
        }
        bufferInfo.presentationTimeUs -= audioBaseTimeUs

        try {
            mediaMuxer?.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error writing audio sample")
        }
    }

    private fun stopMuxer() {
        try {
            if (muxerStarted.get()) {
                mediaMuxer?.stop()
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error stopping MediaMuxer")
        }
        try {
            mediaMuxer?.release()
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error releasing MediaMuxer")
        }
        mediaMuxer = null
        muxerStarted.set(false)
    }

    private fun cleanup() {
        videoTrackIndex = -1
        audioTrackIndex = -1
        videoTrackReady.set(false)
        audioTrackReady.set(false)
        videoBaseTimeUs = -1
        audioBaseTimeUs = -1
    }
}

/**
 * 录制器状态机
 *
 * Idle → Preparing → Recording → Stopping → Completed
 * 任何状态 → Error → Idle
 */
enum class RecorderState {
    /** 空闲，等待开始录制 */
    Idle,

    /** 准备中，正在初始化编码器和复用器 */
    Preparing,

    /** 录制中 */
    Recording,

    /** 正在停止，等待编码器刷新 */
    Stopping,

    /** 录制完成 */
    Completed,

    /** 错误状态，需要重置到 Idle */
    Error
}
