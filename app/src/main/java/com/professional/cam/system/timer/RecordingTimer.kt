package com.professional.cam.system.timer

import com.professional.cam.system.base.SystemMonitor
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 录制计时器
 *
 * 记录录制时长。
 * 提供开始、停止、暂停、恢复功能。
 * 以毫秒精度计时，每秒更新一次。
 */
class RecordingTimer @Inject constructor() : SystemMonitor<Long> {

    override val name: String = "RecordingTimer"

    private val _currentValue = MutableStateFlow(0L)
    override val currentValue: StateFlow<Long> = _currentValue.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // 计时状态
    private var startTimeMs: Long = 0
    private var accumulatedTimeMs: Long = 0
    private var isPaused = false

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        startTimeMs = System.currentTimeMillis()
        accumulatedTimeMs = 0
        isPaused = false

        timerJob = scope.launch {
            while (isActive && _isActive.value) {
                if (!isPaused) {
                    _currentValue.value = accumulatedTimeMs +
                            (System.currentTimeMillis() - startTimeMs)
                }
                delay(100) // 100ms 更新一次，UI 显示每秒更新
            }
        }

        Logger.d(Logger.Tag.SYSTEM, "Recording timer started")
    }

    override fun stop() {
        _isActive.value = false
        timerJob?.cancel()
        timerJob = null
        _currentValue.value = 0L
        accumulatedTimeMs = 0
        Logger.d(Logger.Tag.SYSTEM, "Recording timer stopped")
    }

    override fun getFormattedValue(): String {
        val totalMs = _currentValue.value
        val hours = (totalMs / 3_600_000).toInt()
        val minutes = ((totalMs % 3_600_000) / 60_000).toInt()
        val seconds = ((totalMs % 60_000) / 1000).toInt()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 暂停计时
     */
    fun pause() {
        if (!_isActive.value || isPaused) return
        isPaused = true
        accumulatedTimeMs += System.currentTimeMillis() - startTimeMs
        Logger.d(Logger.Tag.SYSTEM, "Recording timer paused: ${getFormattedValue()}")
    }

    /**
     * 恢复计时
     */
    fun resume() {
        if (!_isActive.value || !isPaused) return
        isPaused = false
        startTimeMs = System.currentTimeMillis()
        Logger.d(Logger.Tag.SYSTEM, "Recording timer resumed")
    }

    /**
     * 获取当前时长（毫秒）
     */
    fun getDurationMs(): Long = _currentValue.value

    /**
     * 获取格式化的时长字符串
     */
    fun getFormattedDuration(): String = getFormattedValue()
}
