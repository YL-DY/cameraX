package com.professional.cam.control.focus

import android.hardware.camera2.CaptureRequest
import com.professional.cam.control.base.CameraControl
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手动对焦控制器
 *
 * 控制相机对焦。
 * 支持自动对焦（AF）和手动对焦（MF）模式。
 *
 * 对焦值范围：0.0（无限远）~ maxFocusDistance（最近对焦距离）
 * 通常 maxFocusDistance 在 0.0 ~ 20.0 之间
 */
class ManualFocusController(
    private val maxFocusDistance: Float?
) : CameraControl<Float> {

    override val name: String = "Focus"

    private val _currentValue = MutableStateFlow(0.0f)
    override val currentValue: StateFlow<Float> = _currentValue.asStateFlow()

    private val _isAuto = MutableStateFlow(true)
    override val isAuto: StateFlow<Boolean> = _isAuto.asStateFlow()

    private var lastManualValue: Float = 0.0f

    /** 对焦模式 */
    private var focusMode: FocusMode = FocusMode.AUTO

    override fun setValue(value: Float) {
        val clamped = value.coerceIn(0.0f, maxFocusDistance ?: 1.0f)
        _currentValue.value = clamped
        _isAuto.value = false
        lastManualValue = clamped
        focusMode = FocusMode.MANUAL
        Logger.d(Logger.Tag.CONTROL, "Focus set to: $clamped")
    }

    override fun setAuto() {
        _isAuto.value = true
        focusMode = FocusMode.AUTO
        Logger.d(Logger.Tag.CONTROL, "Focus set to AUTO")
    }

    override fun setManual() {
        _isAuto.value = false
        focusMode = FocusMode.MANUAL
        _currentValue.value = lastManualValue
    }

    override fun applyToBuilder(builder: CaptureRequest.Builder) {
        when (focusMode) {
            FocusMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, null)
            }
            FocusMode.MANUAL -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, _currentValue.value)
            }
            FocusMode.MACRO -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_MACRO)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, null)
            }
            FocusMode.CONTINUOUS_VIDEO -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, null)
            }
        }
    }

    override fun reset() {
        _currentValue.value = 0.0f
        _isAuto.value = true
        focusMode = FocusMode.AUTO
    }

    /**
     * 设置对焦模式
     */
    fun setFocusMode(mode: FocusMode) {
        focusMode = mode
        _isAuto.value = mode != FocusMode.MANUAL
        Logger.d(Logger.Tag.CONTROL, "Focus mode set to: $mode")
    }

    /**
     * 获取当前对焦模式
     */
    fun getFocusMode(): FocusMode = focusMode

    /**
     * 获取最大对焦距离
     */
    fun getMaxFocusDistance(): Float = maxFocusDistance ?: 1.0f

    /**
     * 对焦模式
     */
    enum class FocusMode {
        /** 自动对焦（连续拍照模式） */
        AUTO,
        /** 手动对焦 */
        MANUAL,
        /** 微距模式 */
        MACRO,
        /** 连续视频对焦 */
        CONTINUOUS_VIDEO
    }
}
