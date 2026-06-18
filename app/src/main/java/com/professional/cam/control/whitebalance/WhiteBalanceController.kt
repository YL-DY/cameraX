package com.professional.cam.control.whitebalance

import android.hardware.camera2.CaptureRequest
import android.util.Range
import com.professional.cam.control.base.CameraControl
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 白平衡控制器
 *
 * 控制相机白平衡。
 * 支持自动白平衡（AWB）和手动色温模式。
 *
 * 色温范围：通常 2500K ~ 8000K
 * 预设模式：自动、日光、阴天、白炽灯、荧光灯
 */
class WhiteBalanceController(
    private val temperatureRange: Range<Int>?
) : CameraControl<WhiteBalanceController.WhiteBalanceValue> {

    override val name: String = "White Balance"

    private val _currentValue = MutableStateFlow(WhiteBalanceValue.Auto)
    override val currentValue: StateFlow<WhiteBalanceValue> = _currentValue.asStateFlow()

    private val _isAuto = MutableStateFlow(true)
    override val isAuto: StateFlow<Boolean> = _isAuto.asStateFlow()

    private var lastManualValue: WhiteBalanceValue = WhiteBalanceValue.Daylight

    override fun setValue(value: WhiteBalanceValue) {
        _currentValue.value = value
        _isAuto.value = value is WhiteBalanceValue.Auto
        if (value !is WhiteBalanceValue.Auto) {
            lastManualValue = value
        }
        Logger.d(Logger.Tag.CONTROL, "WhiteBalance set to: $value")
    }

    override fun setAuto() {
        _currentValue.value = WhiteBalanceValue.Auto
        _isAuto.value = true
        Logger.d(Logger.Tag.CONTROL, "WhiteBalance set to AUTO")
    }

    override fun setManual() {
        _isAuto.value = false
        _currentValue.value = lastManualValue
    }

    override fun applyToBuilder(builder: CaptureRequest.Builder) {
        when (val wb = _currentValue.value) {
            is WhiteBalanceValue.Auto -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO)
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_FAST)
                builder.set(CaptureRequest.COLOR_CORRECTION_TEMPERATURE, null)
            }
            is WhiteBalanceValue.CustomTemperature -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF)
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                val clamped = wb.temperature.coerceIn(
                    temperatureRange?.lower ?: 2500,
                    temperatureRange?.upper ?: 8000
                )
                builder.set(CaptureRequest.COLOR_CORRECTION_TEMPERATURE, clamped)
            }
            is WhiteBalanceValue.Preset -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, wb.camera2Mode)
                builder.set(CaptureRequest.COLOR_CORRECTION_TEMPERATURE, null)
            }
        }
    }

    override fun reset() {
        _currentValue.value = WhiteBalanceValue.Auto
        _isAuto.value = true
    }

    /**
     * 获取色温范围
     */
    fun getTemperatureRange(): Range<Int> {
        return temperatureRange ?: Range(2500, 8000)
    }

    /**
     * 白平衡值
     */
    sealed class WhiteBalanceValue {
        /** 自动白平衡 */
        data object Auto : WhiteBalanceValue()

        /** 预设模式 */
        data class Preset(
            val name: String,
            val camera2Mode: Int
        ) : WhiteBalanceValue() {
            companion object {
                val Daylight = Preset("Daylight", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
                val Cloudy = Preset("Cloudy", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                val Incandescent = Preset("Incandescent", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
                val Fluorescent = Preset("Fluorescent", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
                val Shade = Preset("Shade", CaptureRequest.CONTROL_AWB_MODE_SHADE)
                val Twilight = Preset("Twilight", CaptureRequest.CONTROL_AWB_MODE_TWILIGHT)
            }
        }

        /** 自定义色温 */
        data class CustomTemperature(val temperature: Int) : WhiteBalanceValue()
    }

    companion object {
        val Daylight = WhiteBalanceValue.Preset.Daylight
        val Cloudy = WhiteBalanceValue.Preset.Cloudy
        val Incandescent = WhiteBalanceValue.Preset.Incandescent
        val Fluorescent = WhiteBalanceValue.Preset.Fluorescent
    }
}
