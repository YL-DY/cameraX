package com.professional.cam.control.iso

import android.hardware.camera2.CaptureRequest
import android.util.Range
import com.professional.cam.control.base.CameraControl
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ISO 控制器
 *
 * 控制相机 ISO 感光度。
 * 支持自动 ISO 和手动 ISO 模式。
 *
 * 范围：通常 100-6400（取决于设备能力）
 */
class IsoController(
    private val isoRange: Range<Int>?
) : CameraControl<Int> {

    override val name: String = "ISO"

    private val _currentValue = MutableStateFlow(getDefaultIso())
    override val currentValue: StateFlow<Int> = _currentValue.asStateFlow()

    private val _isAuto = MutableStateFlow(true)
    override val isAuto: StateFlow<Boolean> = _isAuto.asStateFlow()

    private var lastManualValue: Int = getDefaultIso()

    override fun setValue(value: Int) {
        val clamped = value.coerceIn(isoRange?.lower ?: 100, isoRange?.upper ?: 6400)
        _currentValue.value = clamped
        _isAuto.value = false
        lastManualValue = clamped
        Logger.d(Logger.Tag.CONTROL, "ISO set to: $clamped")
    }

    override fun setAuto() {
        _isAuto.value = true
        Logger.d(Logger.Tag.CONTROL, "ISO set to AUTO")
    }

    override fun setManual() {
        _isAuto.value = false
        _currentValue.value = lastManualValue
    }

    override fun applyToBuilder(builder: CaptureRequest.Builder) {
        if (_isAuto.value) {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON)
            // 移除手动 ISO 设置
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, null)
        } else {
            // 手动 ISO 时需要关闭自动曝光
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, _currentValue.value)
        }
    }

    override fun reset() {
        _currentValue.value = getDefaultIso()
        _isAuto.value = true
    }

    /**
     * 获取 ISO 范围
     */
    fun getIsoRange(): Range<Int> {
        return isoRange ?: Range(100, 6400)
    }

    private fun getDefaultIso(): Int {
        return isoRange?.lower ?: 100
    }
}
