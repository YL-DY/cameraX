package com.professional.cam.control.base

import android.hardware.camera2.CaptureRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * 相机控制接口
 *
 * 所有专业控制器的统一接口。
 * 每个控制器管理一个特定的相机参数（ISO、快门、白平衡、对焦、曝光补偿）。
 *
 * 设计原则：
 * - 每个控制器只负责一个参数
 * - 通过 applyToBuilder 将参数应用到 CaptureRequest
 * - 通过 StateFlow 暴露当前值和可用范围
 * - 支持自动/手动模式切换
 *
 * @param T 参数值类型
 */
interface CameraControl<T> {

    /** 控制名称 */
    val name: String

    /** 当前值 */
    val currentValue: StateFlow<T>

    /** 是否处于自动模式 */
    val isAuto: StateFlow<Boolean>

    /** 设置值 */
    fun setValue(value: T)

    /** 切换到自动模式 */
    fun setAuto()

    /** 切换到手动模式 */
    fun setManual()

    /** 将当前参数应用到 CaptureRequest.Builder */
    fun applyToBuilder(builder: CaptureRequest.Builder)

    /** 重置为默认值 */
    fun reset()
}
