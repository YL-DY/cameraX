package com.professional.cam.camera.config

import android.util.Size

/**
 * 相机配置
 *
 * 包含相机初始化所需的所有配置参数。
 */
data class CameraConfig(
    val cameraId: String = "0",
    val targetPreviewSize: Size? = null,
    val targetVideoSize: Size? = null,
    val targetFps: Int = 30,
    val enableAudio: Boolean = true,
    val enableStabilization: Boolean = false,
    val sensorOrientation: Int = 0
)
