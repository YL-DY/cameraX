package com.professional.cam.camera.preview

import android.view.Surface

/**
 * 预览 Surface 提供者接口
 *
 * 职责：提供 [Surface] 给 [com.professional.cam.camera.manager.CameraController]
 * 用于创建 [android.hardware.camera2.CameraCaptureSession]。
 *
 * 设计原则：
 * - 实现类可以是 [android.view.SurfaceView]、[android.view.TextureView]
 *   或其他任何能提供 [Surface] 的组件
 * - 通过此接口解耦 [com.professional.cam.camera.manager.CameraController]
 *   与具体 View 实现
 * - [com.professional.cam.camera.manager.CameraController] 只依赖 [Surface]，
 *   不依赖 View 类型
 *
 * 使用方式：
 * ```
 * class SurfaceViewProvider : PreviewSurfaceProvider {
 *     private val callbacks = mutableListOf<(Surface?) -> Unit>()
 *     private var currentSurface: Surface? = null
 *
 *     override val surface: Surface? get() = currentSurface
 *
 *     override fun onSurfaceChanged(callback: (Surface?) -> Unit) {
 *         callbacks.add(callback)
 *     }
 *
 *     override fun removeSurfaceChangedCallback() {
 *         callbacks.clear()
 *     }
 * }
 * ```
 */
interface PreviewSurfaceProvider {

    /**
     * 当前可用的 [Surface]
     *
     * 可能为 null：
     * - Surface 尚未创建
     * - Surface 已被销毁
     * - View 尚未附加到窗口
     */
    val surface: Surface?

    /**
     * 注册 Surface 状态变化监听
     *
     * 当 Surface 创建、大小变化或销毁时通知回调。
     * 回调参数：
     * - 非 null：Surface 可用
     * - null：Surface 已销毁
     *
     * @param callback Surface 状态回调
     */
    fun onSurfaceChanged(callback: (Surface?) -> Unit)

    /**
     * 移除所有 Surface 状态监听
     *
     * 在组件销毁时调用，防止内存泄漏。
     */
    fun removeSurfaceChangedCallback()
}
