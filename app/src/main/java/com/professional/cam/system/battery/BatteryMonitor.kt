package com.professional.cam.system.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
 * 电池监控器
 *
 * 监控电池电量和温度。
 * 使用 BatteryManager 获取电池状态。
 *
 * 更新频率：每 5 秒
 */
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemMonitor<BatteryMonitor.BatteryInfo> {

    override val name: String = "Battery"

    private val _currentValue = MutableStateFlow(BatteryInfo())
    override val currentValue: StateFlow<BatteryInfo> = _currentValue.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        monitorJob = scope.launch {
            while (isActive && _isActive.value) {
                updateBatteryInfo()
                delay(5000) // 每 5 秒更新
            }
        }

        Logger.d(Logger.Tag.SYSTEM, "Battery monitor started")
    }

    override fun stop() {
        _isActive.value = false
        monitorJob?.cancel()
        monitorJob = null
        Logger.d(Logger.Tag.SYSTEM, "Battery monitor stopped")
    }

    override fun getFormattedValue(): String {
        val info = _currentValue.value
        return "Battery: ${info.level}% ${if (info.isCharging) "⚡" else ""} ${info.temperature}°C"
    }

    private suspend fun updateBatteryInfo() {
        try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val batteryPct = (level * 100 / scale)

                val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val healthStatus = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
                    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
                    BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
                    else -> BatteryHealth.UNKNOWN
                }

                _currentValue.value = BatteryInfo(
                    level = batteryPct,
                    temperature = temperature,
                    isCharging = isCharging,
                    health = healthStatus
                )
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.SYSTEM, e, "Failed to update battery info")
        }
    }

    /**
     * 电池信息
     */
    data class BatteryInfo(
        val level: Int = 0,
        val temperature: Float = 0f,
        val isCharging: Boolean = false,
        val health: BatteryHealth = BatteryHealth.UNKNOWN
    )

    enum class BatteryHealth {
        GOOD,
        OVERHEAT,
        DEAD,
        OVER_VOLTAGE,
        COLD,
        UNKNOWN
    }
}
