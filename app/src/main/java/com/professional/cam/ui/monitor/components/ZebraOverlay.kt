package com.professional.cam.ui.monitor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.ui.theme.ZebraColor

/**
 * 斑马纹叠加层
 *
 * 在过曝区域绘制斑马纹。
 * 覆盖在整个预览画面上。
 */
@Composable
fun ZebraOverlay(
    zebraData: AnalysisResult.ZebraData?,
    modifier: Modifier = Modifier
) {
    if (zebraData != null && zebraData.overexposedRegions.isNotEmpty()) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val viewWidth = size.width
            val viewHeight = size.height

            // 假设预览分辨率为 1920x1080
            val scaleX = viewWidth / 1920f
            val scaleY = viewHeight / 1080f

            zebraData.overexposedRegions.forEach { region ->
                drawZebraPattern(
                    x = region.x * scaleX,
                    y = region.y * scaleY,
                    width = region.width * scaleX,
                    height = region.height * scaleY
                )
            }
        }
    }
}

private fun DrawScope.drawZebraPattern(
    x: Float,
    y: Float,
    width: Float,
    height: Float
) {
    val stripeWidth = 8f
    val stripeGap = 8f
    var currentX = x

    while (currentX < x + width) {
        drawRect(
            color = ZebraColor.copy(alpha = 0.4f),
            topLeft = Offset(currentX, y),
            size = androidx.compose.ui.geometry.Size(
                stripeWidth.coerceAtMost(x + width - currentX),
                height
            )
        )
        currentX += stripeWidth + stripeGap
    }
}
