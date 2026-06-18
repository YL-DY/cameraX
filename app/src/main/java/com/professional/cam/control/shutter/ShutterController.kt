package com.professional.cam.control.shutter

import android.hardware.camera2.CaptureRequest
import android.util.Range
import com.professional.cam.control.base.CameraControl
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 快门速度控制器
 *
 * 控制相机快门速度（曝光时间）。
 * 支持自动快门和手动快门模式。
 *
 * 值单位：纳秒（1秒 = 1_000_000_000ns）
 * 范围：通常 1/8000s (125000ns) ~ 30s (30000000000ns)
 */
class ShutterController(
    private val shutterRange: Range<Long>?
) : CameraControl<Long> {

    override val name: String = "Shutter"

    private val _currentValue = MutableStateFlow(getDefaultShutter())
    override val currentValue: StateFlow<Long> = _currentValue.asStateFlow()

    private val _isAuto = MutableStateFlow(true)
    override val isAuto: StateFlow<Boolean> = _isAuto.asStateFlow()

    private var lastManualValue: Long = getDefaultShutter()

    override fun setValue(value: Long) {
        val clamped = value.coerceIn(
            shutterRange?.lower ?: MIN_SHUTTER_NS,
            shutterRange?.upper ?: MAX_SHUTTER_NS
        )
        _currentValue.value = clamped
        _isAuto.value = false
        lastManualValue = clamped
        Logger.d(Logger.Tag.CONTROL, "Shutter set to: ${formatShutterSpeed(clamped)}")
    }

    override fun setAuto() {
        _isAuto.value = true
        Logger.d(Logger.Tag.CONTROL, "Shutter set to AUTO")
    }

    override fun setManual() {
        _isAuto.value = false
        _currentValue.value = lastManualValue
    }

    override fun applyToBuilder(builder: CaptureRequest.Builder) {
        if (_isAuto.value) {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, null)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, _currentValue.value)
        }
    }

    override fun reset() {
        _currentValue.value = getDefaultShutter()
        _isAuto.value = true
    }

    /**
     * 获取快门速度范围（纳秒）
     */
    fun getShutterRange(): Range<Long> {
        return shutterRange ?: Range(MIN_SHUTTER_NS, MAX_SHUTTER_NS)
    }

    /**
     * 将纳秒格式化为可读的快门速度字符串
     */
    fun formatShutterSpeed(nanos: Long): String {
        return when {
            nanos >= 1_000_000_000L -> "${nanos / 1_000_000_000}.${(nanos % 1_000_000_000) / 100_000_000}s"
            nanos >= 1_000_000L -> "1/${1_000_000_000L / nanos}"
            else -> "1/${1_000_000_000L / nanos}"
        }
    }

    companion object {
        /** 最快快门：1/8000s */
        const val MIN_SHUTTER_NS = 125_000L
        /** 最慢快门：30s */
        const val MAX_SHUTTER_NS = 30_000_000_000L
        /** 默认快门：1/60s */
        const val DEFAULT_SHUTTER_NS = 16_666_667L
    }

    private fun getDefaultShutter(): Long {
        return shutterRange?.let {
            it.lower.coerceAtLeast(MIN_SHUTTER_NS)
        } ?: DEFAULT_SHUTTER_NS
    }
}
