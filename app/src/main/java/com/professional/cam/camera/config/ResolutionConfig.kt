package com.professional.cam.camera.config

import android.util.Size

/**
 * 分辨率配置
 *
 * 提供标准分辨率列表和选择逻辑。
 */
data class ResolutionConfig(
    val width: Int,
    val height: Int,
    val label: String
) {
    val size: Size get() = Size(width, height)
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
    val megapixels: Float get() = (width * height) / 1_000_000f

    companion object {
        /** 标准视频分辨率列表 */
        val STANDARD_VIDEO_RESOLUTIONS = listOf(
            ResolutionConfig(3840, 2160, "4K UHD"),
            ResolutionConfig(2560, 1440, "2K QHD"),
            ResolutionConfig(1920, 1080, "1080p FHD"),
            ResolutionConfig(1280, 720, "720p HD"),
            ResolutionConfig(854, 480, "480p SD"),
            ResolutionConfig(640, 360, "360p SD")
        )

        /** 标准预览分辨率列表 */
        val STANDARD_PREVIEW_RESOLUTIONS = listOf(
            ResolutionConfig(1920, 1080, "1080p"),
            ResolutionConfig(1280, 720, "720p"),
            ResolutionConfig(960, 540, "540p"),
            ResolutionConfig(640, 480, "480p")
        )

        /** 默认录像分辨率 */
        val DEFAULT_VIDEO = STANDARD_VIDEO_RESOLUTIONS[2] // 1080p

        /** 默认预览分辨率 */
        val DEFAULT_PREVIEW = STANDARD_PREVIEW_RESOLUTIONS[0] // 1080p
    }
}
