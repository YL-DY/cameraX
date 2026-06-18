package com.professional.cam.system.memory

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.professional.cam.system.base.SystemMonitor
import com.professional.cam.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 内存监控器
 *
 * 监控应用内存使用情况。
 * 包括：已用内存、可用内存、堆内存、Java 堆使用率。
 *
 * 更新频率：每 3 秒
 */
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemMonitor<MemoryMonitor.MemoryInfo> {

    override val name: String = "Memory"

    private val _currentValue = MutableStateFlow(MemoryInfo())
    override val currentValue: StateFlow<MemoryInfo> = _currentValue.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val activityManager: ActivityManager?
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        monitorJob = scope.launch {
            while (isActive && _isActive.value) {
                updateMemoryInfo()
                delay(3000) // 每 3 秒更新
            }
        }

        Logger.d(Logger.Tag.SYSTEM, "Memory monitor started")
    }

    override fun stop() {
        _isActive.value = false
        monitorJob?.cancel()
        monitorJob = null
        Logger.d(Logger.Tag.SYSTEM, "Memory monitor stopped")
    }

    override fun getFormattedValue(): String {
        val info = _currentValue.value
        return "RAM: ${info.usedMemoryMb}/${info.totalMemoryMb}MB (${info.heapUsagePercent}%)"
    }

    private suspend fun updateMemoryInfo() {
        try {
            val runtime = Runtime.getRuntime()
            val maxHeapMemory = runtime.maxMemory()
            val totalHeapMemory = runtime.totalMemory()
            val freeHeapMemory = runtime.freeMemory()
            val usedHeapMemory = totalHeapMemory - freeHeapMemory

            val heapUsagePercent = if (maxHeapMemory > 0) {
                ((usedHeapMemory * 100) / maxHeapMemory).toInt()
            } else 0

            // 系统内存信息
            val memInfo = activityManager?.let { am ->
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                mi
            }

            val totalMemMb = (memInfo?.totalMem ?: 0) / (1024 * 1024)
            val availMemMb = (memInfo?.availMem ?: 0) / (1024 * 1024)
            val usedMemMb = totalMemMb - availMemMb
            val isLowMemory = memInfo?.lowMemory ?: false

            // Dalvik 堆内存
            val dalvikHeapMb = usedHeapMemory / (1024 * 1024)
            val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

            _currentValue.value = MemoryInfo(
                totalMemoryMb = totalMemMb.toInt(),
                usedMemoryMb = usedMemMb.toInt(),
                availableMemoryMb = availMemMb.toInt(),
                heapUsagePercent = heapUsagePercent,
                dalvikHeapMb = dalvikHeapMb.toInt(),
                nativeHeapMb = nativeHeapMb.toInt(),
                isLowMemory = isLowMemory
            )
        } catch (e: Exception) {
            Logger.e(Logger.Tag.SYSTEM, e, "Failed to update memory info")
        }
    }

    /**
     * 内存信息
     */
    data class MemoryInfo(
        val totalMemoryMb: Int = 0,
        val usedMemoryMb: Int = 0,
        val availableMemoryMb: Int = 0,
        val heapUsagePercent: Int = 0,
        val dalvikHeapMb: Int = 0,
        val nativeHeapMb: Int = 0,
        val isLowMemory: Boolean = false
    )
}
