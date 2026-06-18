package com.professional.cam.system.storage

import android.os.Environment
import android.os.StatFs
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
 * 存储监控器
 *
 * 监控设备存储空间。
 * 包括：总空间、已用空间、可用空间。
 *
 * 更新频率：每 10 秒
 */
class StorageMonitor @Inject constructor() : SystemMonitor<StorageMonitor.StorageInfo> {

    override val name: String = "Storage"

    private val _currentValue = MutableStateFlow(StorageInfo())
    override val currentValue: StateFlow<StorageInfo> = _currentValue.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        monitorJob = scope.launch {
            while (isActive && _isActive.value) {
                updateStorageInfo()
                delay(10000) // 每 10 秒更新
            }
        }

        Logger.d(Logger.Tag.SYSTEM, "Storage monitor started")
    }

    override fun stop() {
        _isActive.value = false
        monitorJob?.cancel()
        monitorJob = null
        Logger.d(Logger.Tag.SYSTEM, "Storage monitor stopped")
    }

    override fun getFormattedValue(): String {
        val info = _currentValue.value
        return "Storage: ${info.usedFormatted}/${info.totalFormatted} (${info.usagePercent}%)"
    }

    private suspend fun updateStorageInfo() {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)

            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            val usagePercent = if (totalBytes > 0) {
                ((usedBytes * 100) / totalBytes).toInt()
            } else 0

            _currentValue.value = StorageInfo(
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                availableBytes = availableBytes,
                usagePercent = usagePercent
            )
        } catch (e: Exception) {
            Logger.e(Logger.Tag.SYSTEM, e, "Failed to update storage info")
        }
    }

    /**
     * 存储信息
     */
    data class StorageInfo(
        val totalBytes: Long = 0,
        val usedBytes: Long = 0,
        val availableBytes: Long = 0,
        val usagePercent: Int = 0
    ) {
        val totalFormatted: String get() = formatBytes(totalBytes)
        val usedFormatted: String get() = formatBytes(usedBytes)
        val availableFormatted: String get() = formatBytes(availableBytes)

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000_000L -> String.format("%.1f TB", bytes / 1_000_000_000_000.0)
                bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
                bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }
    }
}
