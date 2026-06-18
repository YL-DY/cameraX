package com.professional.cam.camera.session

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.professional.cam.camera.audio.AudioRecorder
import com.professional.cam.camera.video.VideoEncoder
import com.professional.cam.core.util.Logger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 录制会话
 *
 * 协调视频编码器、音频录制器和 MediaMuxer 的工作。
 * 职责：
 * - 管理录制生命周期（开始/停止/暂停/恢复）
 * - 同步音视频轨道到 MediaMuxer
 * - 处理录制分段（最大时长/文件大小）
 * - 错误恢复
 *
 * 设计原则：
 * - 单次录制 = 一个 RecordingSession 实例
 * - 支持无音频录制（音频不可用时自动降级）
 * - 支持录制分段（长视频自动分段）
 * - 所有操作线程安全
 */
class RecordingSession @Inject constructor(
    private val videoEncoder: VideoEncoder,
    private val audioRecorder: AudioRecorder
) {

    companion object {
        private const val MAX_DURATION_MS = 30 * 60 * 1000L // 30分钟
        private const val MAX_FILE_SIZE_BYTES = 4L * 1024 * 1024 * 1024 // 4GB
        private const val MIN_SEGMENT_DURATION_MS = 5_000L // 最小分段5秒
    }

    // 录制状态
    private val _isRecording = AtomicBoolean(false)
    val isRecording: Boolean get() = _isRecording.get()

    private val _isPaused = AtomicBoolean(false)
    val isPaused: Boolean get() = _isPaused.get()

    // MediaMuxer
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = AtomicBoolean(false)

    // 轨道就绪标志
    private var videoTrackReady = AtomicBoolean(false)
    private var audioTrackReady = AtomicBoolean(false)

    // 时间戳偏移
    private var videoBaseTimeUs: Long = -1
    private var audioBaseTimeUs: Long = -1

    // 录制限制
    private var startTimeMs: Long = 0
    private var segmentStartTimeMs: Long = 0
    private var currentSegmentIndex = AtomicInteger(0)

    // 输出路径
    private var outputPath: String = ""
    private var onSegmentComplete: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onDurationUpdate: ((Long) -> Unit)? = null

    // 录制配置
    private var maxDurationMs: Long = MAX_DURATION_MS
    private var maxFileSize: Long = MAX_FILE_SIZE_BYTES
    private var enableAudio: Boolean = true
    private var hasAudio: Boolean = false

    /**
     * 设置录制分段完成回调
     */
    fun setOnSegmentCompleteCallback(callback: (String) -> Unit) {
        onSegmentComplete = callback
    }

    /**
     * 设置错误回调
     */
    fun setOnErrorCallback(callback: (String) -> Unit) {
        onError = callback
    }

    /**
     * 设置时长更新回调
     */
    fun setOnDurationUpdateCallback(callback: (Long) -> Unit) {
        onDurationUpdate = callback
    }

    /**
     * 配置录制参数
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
            onError?.invoke("Video encoder: $error")
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
     * @return 视频输入 Surface（Camera2 渲染目标）
     */
    fun start(): Surface? {
        if (_isRecording.get()) {
            Logger.w(Logger.Tag.RECORDING, "RecordingSession already started")
            return videoEncoder.getInputSurface()
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
                onError?.invoke("Video encoder failed to start")
                cleanup()
                return null
            }

            // 启动音频录制器
            if (enableAudio) {
                hasAudio = audioRecorder.start()
            }

            _isRecording.set(true)
            startTimeMs = System.currentTimeMillis()
            segmentStartTimeMs = startTimeMs

            Logger.d(Logger.Tag.RECORDING, "RecordingSession started: $outputPath")
            return surface
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to start RecordingSession")
            onError?.invoke("Recording start failed: ${e.message}")
            cleanup()
            return null
        }
    }

    /**
     * 停止录制
     */
    fun stop() {
        if (!_isRecording.getAndSet(false)) return

        Logger.d(Logger.Tag.RECORDING, "RecordingSession stopping...")

        // 停止编码器
        videoEncoder.stop()
        if (hasAudio) {
            audioRecorder.stop()
        }

        // 停止并释放 MediaMuxer
        stopMuxer()

        cleanup()

        val duration = System.currentTimeMillis() - startTimeMs
        Logger.d(Logger.Tag.RECORDING, "RecordingSession stopped. Duration: ${duration}ms")
    }

    /**
     * 暂停录制
     */
    fun pause() {
        if (!_isRecording.get() || _isPaused.get()) return
        _isPaused.set(true)
        Logger.d(Logger.Tag.RECORDING, "RecordingSession paused")
    }

    /**
     * 恢复录制
     */
    fun resume() {
        if (!_isRecording.get() || !_isPaused.get()) return
        _isPaused.set(false)
        Logger.d(Logger.Tag.RECORDING, "RecordingSession resumed")
    }

    /**
     * 获取当前录制时长（毫秒）
     */
    fun getCurrentDurationMs(): Long {
        if (!_isRecording.get()) return 0
        return System.currentTimeMillis() - startTimeMs
    }

    /**
     * 是否需要分段（达到时长或文件大小限制）
     */
    fun shouldCreateNewSegment(): Boolean {
        val duration = getCurrentDurationMs()
        return duration >= maxDurationMs || duration >= MIN_SEGMENT_DURATION_MS
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        videoEncoder.release()
        audioRecorder.release()
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
                onError?.invoke("Muxer start failed: ${e.message}")
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
