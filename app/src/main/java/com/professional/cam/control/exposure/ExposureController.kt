package com.professional.cam.control.exposure

import android.hardware.camera2.CaptureRequest
import android.util.Range
import com.professional.cam.control.base.CameraControl
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 曝光补偿控制器
 *
 * 控制相机曝光补偿（EV）。
 * 在自动曝光模式下调整曝光值。
 *
 * 范围：通常 -12 ~ +12（以 1/3 EV 为步长）
 * 步长：通常 1/3 EV
 */
class ExposureController(
    private val compensationRange: Range<Int>?,
    private val compensationStep: Rational?
) : CameraControl<Int> {

    override val name: String = "Exposure"

    private val _currentValue = MutableStateFlow(0)
    override val currentValue: StateFlow<Int> = _currentValue.asStateFlow()

    private val _isAuto = MutableStateFlow(false) // EV 补偿总是手动设置
    override val isAuto: StateFlow<Boolean> = _isAuto.asStateFlow()

    override fun setValue(value: Int) {
        val clamped = value.coerceIn(
            compensationRange?.lower ?: -12,
            compensationRange?.upper ?: 12
        )
        _currentValue.value = clamped
        Logger.d(Logger.Tag.CONTROL, "Exposure compensation set to: $clamped")
    }

    override fun setAuto() {
        _currentValue.value = 0
        Logger.d(Logger.Tag.CONTROL, "Exposure compensation reset to 0")
    }

    override fun setManual() {
        // EV 补偿始终是手动模式
    }

    override fun applyToBuilder(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, _currentValue.value)
    }

    override fun reset() {
        _currentValue.value = 0
    }

    /**
     * 增加曝光补偿
     */
    fun increase() {
        val step = compensationStep?.let { it.numerator.toFloat() / it.denominator.toFloat() } ?: 1f
        setValue(_currentValue.value + step.toInt().coerceAtLeast(1))
    }

    /**
     * 减少曝光补偿
     */
    fun decrease() {
        val step = compensationStep?.let { it.numerator.toFloat() / it.denominator.toFloat() } ?: 1f
        setValue(_currentValue.value - step.toInt().coerceAtLeast(1))
    }

    /**
     * 获取曝光补偿范围
     */
    fun getCompensationRange(): Range<Int> {
        return compensationRange ?: Range(-12, 12)
    }

    /**
     * 获取曝光补偿步长
     */
    fun getCompensationStep(): Rational {
        return compensationStep ?: Rational(1, 3)
    }

    /**
     * 获取当前 EV 值的可读字符串
     */
    fun getEvString(): String {
        val value = _currentValue.value
        val step = compensationStep?.let {
            it.numerator.toFloat() / it.denominator.toFloat()
        } ?: 1f
        val ev = value * step
        return "${if (ev >= 0) "+" else ""}${String.format("%.1f", ev)} EV"
    }

    /**
     * 有理数（用于表示步长）
     */
    data class Rational(
        val numerator: Int,
        val denominator: Int
    )
}
