package com.professional.cam.system.cpu

import android.os.Process
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
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * CPU 监控器
 *
 * 监控 CPU 使用率和频率。
 * 读取 /proc/stat 计算 CPU 使用率。
 * 读取 /sys/devices/system/cpu 获取 CPU 频率。
 *
 * 更新频率：每 2 秒
 */
class CpuMonitor @Inject constructor() : SystemMonitor<CpuMonitor.CpuInfo> {

    override val name: String = "CPU"

    private val _currentValue = MutableStateFlow(CpuInfo())
    override val currentValue: StateFlow<CpuInfo> = _currentValue.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // 上次 CPU 时间
    private var lastCpuTime: Long = 0
    private var lastIdleTime: Long = 0
    private var lastProcessCpuTime: Long = 0

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        monitorJob = scope.launch {
            // 初始化
            readCpuTimes()?.let { (cpu, idle) ->
                lastCpuTime = cpu
                lastIdleTime = idle
            }
            lastProcessCpuTime = readProcessCpuTime()

            while (isActive && _isActive.value) {
                updateCpuInfo()
                delay(2000) // 每 2 秒更新
            }
        }

        Logger.d(Logger.Tag.SYSTEM, "CPU monitor started")
    }

    override fun stop() {
        _isActive.value = false
        monitorJob?.cancel()
        monitorJob = null
        Logger.d(Logger.Tag.SYSTEM, "CPU monitor stopped")
    }

    override fun getFormattedValue(): String {
        val info = _currentValue.value
        return "CPU: ${info.usagePercent}% (${info.currentFreqMHz}MHz)"
    }

    private suspend fun updateCpuInfo() {
        try {
            // 总 CPU 使用率
            val totalUsage = calculateTotalCpuUsage()

            // 进程 CPU 使用率
            val processUsage = calculateProcessCpuUsage()

            // CPU 频率
            val freq = readCpuFreq()

            _currentValue.value = CpuInfo(
                usagePercent = totalUsage,
                processUsagePercent = processUsage,
                currentFreqMHz = freq,
                coreCount = Runtime.getRuntime().availableProcessors()
            )
        } catch (e: Exception) {
            Logger.e(Logger.Tag.SYSTEM, e, "Failed to update CPU info")
        }
    }

    private fun calculateTotalCpuUsage(): Int {
        val times = readCpuTimes() ?: return 0
        val (cpuTime, idleTime) = times

        val totalDelta = cpuTime - lastCpuTime
        val idleDelta = idleTime - lastIdleTime

        lastCpuTime = cpuTime
        lastIdleTime = idleTime

        return if (totalDelta > 0) {
            ((totalDelta - idleDelta) * 100 / totalDelta).toInt().coerceIn(0, 100)
        } else 0
    }

    private fun calculateProcessCpuUsage(): Int {
        val processCpuTime = readProcessCpuTime()
        val delta = processCpuTime - lastProcessCpuTime
        lastProcessCpuTime = processCpuTime

        // 2秒间隔内的 CPU 使用率
        val maxPossibleCpuTime = 2 * 1000 * 1000 * 1000L // 2秒 * 1000 * 1000 * 1000
        return if (maxPossibleCpuTime > 0) {
            ((delta * 100) / maxPossibleCpuTime).toInt().coerceIn(0, 100)
        } else 0
    }

    private fun readCpuTimes(): Pair<Long, Long>? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { file ->
                val line = file.readLine() ?: return null
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 5) return null

                var cpuTime = 0L
                for (i in 1 until parts.size) {
                    cpuTime += parts[i].toLong()
                }
                val idleTime = parts[4].toLong()
                Pair(cpuTime, idleTime)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readProcessCpuTime(): Long {
        return try {
            RandomAccessFile("/proc/self/stat", "r").use { file ->
                val line = file.readLine() ?: return 0L
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 15) return 0L
                // utime (14) + stime (15)
                parts[13].toLong() + parts[14].toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun readCpuFreq(): Int {
        return try {
            RandomAccessFile(
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
                "r"
            ).use { file ->
                (file.readLine().toLong() / 1000).toInt() // kHz -> MHz
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * CPU 信息
     */
    data class CpuInfo(
        val usagePercent: Int = 0,
        val processUsagePercent: Int = 0,
        val currentFreqMHz: Int = 0,
        val coreCount: Int = 0
    )
}
