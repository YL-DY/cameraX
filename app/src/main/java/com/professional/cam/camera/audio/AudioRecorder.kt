package com.professional.cam.camera.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import com.professional.cam.core.util.Logger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * 音频录制器
 *
 * 使用 AudioRecord + MediaCodec 进行 AAC 音频编码。
 * 职责：
 * - 从麦克风采集 PCM 音频数据
 * - 使用 MediaCodec 编码为 AAC
 * - 输出编码后的数据到回调
 *
 * 设计原则：
 * - 独立线程运行，不阻塞主线程
 * - 支持采样率自动降级
 * - 静音检测（可选）
 * - 错误时自动降级（可继续无音频录制）
 */
class AudioRecorder @Inject constructor() {

    companion object {
        // 音频配置
        private const val SAMPLE_RATE_HZ = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        // AAC 编码配置
        private const val MIME_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
        private const val BIT_RATE = 128_000 // 128 kbps

        // AudioRecord 缓冲区大小
        private const val BUFFER_SIZE_MULTIPLIER = 2
        private const val TIMEOUT_US = 10_000L
    }

    // 状态
    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null
    private var isRunning = AtomicBoolean(false)
    private var isMuted = AtomicBoolean(false)

    // 缓冲区
    private var bufferSize: Int = 0
    private var pcmBuffer: ByteBuffer? = null

    // 采样率（可能降级）
    private var currentSampleRate: Int = SAMPLE_RATE_HZ

    // 回调
    private var onEncodedData: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    private var onFormatChanged: ((MediaFormat) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    // 线程
    private var audioThread: Thread? = null

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
     * 设置静音
     */
    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    /**
     * 启动音频录制
     *
     * @return true 如果启动成功，false 如果音频不可用
     */
    fun start(): Boolean {
        if (isRunning.get()) {
            Logger.w(Logger.Tag.RECORDING, "AudioRecorder already running")
            return true
        }

        // 初始化 AudioRecord
        if (!initAudioRecord()) {
            Logger.w(Logger.Tag.RECORDING, "AudioRecord init failed, continuing without audio")
            onError?.invoke("Audio unavailable")
            return false
        }

        // 初始化 MediaCodec AAC 编码器
        if (!initAudioEncoder()) {
            Logger.w(Logger.Tag.RECORDING, "Audio encoder init failed, continuing without audio")
            audioRecord?.release()
            audioRecord = null
            onError?.invoke("Audio encoder unavailable")
            return false
        }

        isRunning.set(true)
        startAudioThread()

        Logger.d(Logger.Tag.RECORDING, "AudioRecorder started: ${currentSampleRate}Hz, AAC")
        return true
    }

    /**
     * 停止音频录制
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            audioThread?.join(2000)
            audioThread = null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // 发送 EOS 到编码器
        sendEndOfStream()

        // 释放 AudioRecord
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error releasing AudioRecord")
        }
        audioRecord = null

        // 释放 MediaCodec
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error releasing audio encoder")
        }
        mediaCodec = null

        Logger.d(Logger.Tag.RECORDING, "AudioRecorder stopped")
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

    private fun initAudioRecord(): Boolean {
        return try {
            // 尝试首选采样率，失败则降级
            currentSampleRate = findSupportedSampleRate()

            bufferSize = maxOf(
                AudioRecord.getMinBufferSize(currentSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT),
                currentSampleRate * BUFFER_SIZE_MULTIPLIER // 2秒缓冲区
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioRecord = AudioRecord.Builder()
                    .setAudioSource(AUDIO_SOURCE)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(currentSampleRate)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                audioRecord = AudioRecord(
                    AUDIO_SOURCE,
                    currentSampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            pcmBuffer = ByteBuffer.allocateDirect(bufferSize)
            true
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to init AudioRecord")
            false
        }
    }

    private fun initAudioEncoder(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE_AAC, currentSampleRate, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }

            val codec = MediaCodec.createEncoderByType(MIME_TYPE_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mediaCodec = codec
            true
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to init audio encoder")
            false
        }
    }

    private fun startAudioThread() {
        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val record = audioRecord ?: return@Thread
            val codec = mediaCodec ?: return@Thread
            val buffer = pcmBuffer ?: return@Thread

            record.startRecording()

            while (isRunning.get()) {
                try {
                    if (isMuted.get()) {
                        // 静音：写入静音数据
                        buffer.put(ByteArray(buffer.remaining()))
                        buffer.flip()
                    } else {
                        // 从麦克风读取 PCM 数据
                        val bytesRead = record.read(buffer, bufferSize)
                        if (bytesRead > 0) {
                            buffer.position(0)
                            buffer.limit(bytesRead)
                        } else {
                            continue
                        }
                    }

                    // 将 PCM 数据送入编码器
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        inputBuffer?.let {
                            it.clear()
                            it.put(buffer)
                            buffer.clear()
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                it.position(),
                                System.nanoTime() / 1000,
                                0
                            )
                        }
                    }

                    // 获取编码输出
                    drainEncoder(codec)
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Logger.e(Logger.Tag.RECORDING, e, "Audio recording error")
                    }
                }
            }

            // 停止录制
            try {
                record.stop()
            } catch (e: Exception) {
                // 忽略停止时的错误
            }
        }, "AudioRecorder").apply { start() }
    }

    private fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

        while (outputIndex >= 0) {
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatChanged?.invoke(codec.outputFormat)
                    Logger.d(Logger.Tag.RECORDING, "Audio encoder output format changed")
                }

                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        onEncodedData?.invoke(outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
            outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        }
    }

    private fun sendEndOfStream() {
        try {
            val codec = mediaCodec ?: return
            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            // 排空剩余数据
            drainEncoder(codec)
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Error sending EOS to audio encoder")
        }
    }

    private fun findSupportedSampleRate(): Int {
        val preferredRates = intArrayOf(44100, 48000, 32000, 22050, 16000)
        for (rate in preferredRates) {
            val bufferSize = AudioRecord.getMinBufferSize(rate, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize > 0) {
                return rate
            }
        }
        return SAMPLE_RATE_HZ // 回退到默认
    }
}
