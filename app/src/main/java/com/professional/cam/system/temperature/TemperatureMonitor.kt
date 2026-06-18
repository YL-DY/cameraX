package com.professional.cam.system.temperature

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.health.HealthStats
import android.os.health.SystemHealthManager
import android.os.health.TimerStat
import android.os.health.UidHealthStats
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
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * 温度监控器
 *
 * 监控设备温度。
 * 读取 /sys/class/thermal/ 获取各传感器温度。
 * 使用电池温度作为后备。
 *
 * 更新频率：每 5 秒
 */
class TemperatureMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemMonitor<TemperatureMonitor.TemperatureInfo> {

    override val name: String = "Temperature"

    private val _currentValue = MutableStateFlow(TemperatureInfo())
    override val currentValue: StateFlow<TemperatureInfo> = _currentValue.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // 热区类型名称
    private val thermalZones = listOf(
        "cpu", "gpu", "battery", "skin", "soc",
        "cpu0", "cpu1", "cpu2", "cpu3",
        "tsens_tz_sensor0", "tsens_tz_sensor1",
        "pm8994_tz", "xo_therm", "pa_therm0"
    )

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        monitorJob = scope.launch {
            while (isActive && _isActive.value) {
                updateTemperature()
                delay(5000) // 每 5 秒更新
            }
        }

        Logger.d(Logger.Tag.SYSTEM, "Temperature monitor started")
    }

    override fun stop() {
        _isActive.value = false
        monitorJob?.cancel()
        monitorJob = null
        Logger.d(Logger.Tag.SYSTEM, "Temperature monitor stopped")
    }

    override fun getFormattedValue(): String {
        val info = _currentValue.value
        return "Temp: ${info.maxTemperature}°C (CPU: ${info.cpuTemperature}°C)"
    }

    private suspend fun updateTemperature() {
        try {
            var cpuTemp = 0f
            var gpuTemp = 0f
            var batteryTemp = 0f
            var skinTemp = 0f
            var maxTemp = 0f

            // 读取 thermal zone 温度
            for (i in 0..20) {
                val temp = readThermalZoneTemp(i)
                if (temp != null) {
                    val zoneName = readThermalZoneName(i)?.lowercase() ?: ""
                    maxTemp = maxOf(maxTemp, temp)

                    when {
                        zoneName.contains("cpu") -> cpuTemp = maxOf(cpuTemp, temp)
                        zoneName.contains("gpu") -> gpuTemp = maxOf(gpuTemp, temp)
                        zoneName.contains("battery") -> batteryTemp = maxOf(batteryTemp, temp)
                        zoneName.contains("skin") -> skinTemp = maxOf(skinTemp, temp)
                    }
                }
            }

            // 如果没有 thermal zone，使用电池温度
            if (maxTemp == 0f) {
                batteryTemp = readBatteryTemperature()
                maxTemp = batteryTemp
            }

            _currentValue.value = TemperatureInfo(
                cpuTemperature = cpuTemp,
                gpuTemperature = gpuTemp,
                batteryTemperature = batteryTemp,
                skinTemperature = skinTemp,
                maxTemperature = maxTemp
            )
        } catch (e: Exception) {
            Logger.e(Logger.Tag.SYSTEM, e, "Failed to update temperature")
        }
    }

    private fun readThermalZoneTemp(zone: Int): Float? {
        return try {
            RandomAccessFile(
                "/sys/class/thermal/thermal_zone$zone/temp",
                "r"
            ).use { file ->
                val raw = file.readLine().trim()
                if (raw.isNotEmpty()) {
                    raw.toFloat() / 1000f // 毫摄氏度 -> 摄氏度
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readThermalZoneName(zone: Int): String? {
        return try {
            RandomAccessFile(
                "/sys/class/thermal/thermal_zone$zone/type",
                "r"
            ).use { file ->
                file.readLine()?.trim()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readBatteryTemperature(): Float {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            if (intent != null) {
                intent.getIntExtra(
                    android.os.BatteryManager.EXTRA_TEMPERATURE, 0
                ) / 10f
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * 温度信息
     */
    data class TemperatureInfo(
        val cpuTemperature: Float = 0f,
        val gpuTemperature: Float = 0f,
        val batteryTemperature: Float = 0f,
        val skinTemperature: Float = 0f,
        val maxTemperature: Float = 0f
    )
}
