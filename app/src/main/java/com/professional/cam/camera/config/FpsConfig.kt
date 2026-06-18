package com.professional.cam.camera.config

/**
 * FPS 配置
 *
 * 提供标准帧率列表和选择逻辑。
 */
data class FpsConfig(
    val fps: Int,
    val label: String
) {
    companion object {
        /** 标准帧率列表 */
        val STANDARD_FPS_VALUES = listOf(
            FpsConfig(24, "24 fps"),
            FpsConfig(25, "25 fps"),
            FpsConfig(30, "30 fps"),
            FpsConfig(48, "48 fps"),
            FpsConfig(50, "50 fps"),
            FpsConfig(60, "60 fps"),
            FpsConfig(120, "120 fps"),
            FpsConfig(240, "240 fps")
        )

        /** 默认帧率 */
        val DEFAULT = STANDARD_FPS_VALUES[2] // 30 fps
    }
}
