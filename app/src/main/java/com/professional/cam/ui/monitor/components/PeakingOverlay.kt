package com.professional.cam.ui.monitor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.ui.theme.PeakingColor

/**
 * 峰值对焦叠加层
 *
 * 在合焦区域边缘绘制绿色高亮。
 * 覆盖在整个预览画面上。
 */
@Composable
fun PeakingOverlay(
    peakingData: AnalysisResult.PeakingData?,
    modifier: Modifier = Modifier
) {
    if (peakingData != null && peakingData.edgePixels.isNotEmpty()) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val viewWidth = size.width
            val viewHeight = size.height

            // 假设预览分辨率为 1920x1080
            val scaleX = viewWidth / 1920f
            val scaleY = viewHeight / 1080f

            peakingData.edgePixels.forEach { region ->
                drawRect(
                    color = PeakingColor.copy(alpha = 0.6f),
                    topLeft = Offset(
                        region.x * scaleX,
                        region.y * scaleY
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (region.width * scaleX).coerceAtLeast(2f),
                        (region.height * scaleY).coerceAtLeast(2f)
                    )
                )
            }
        }
    }
}
