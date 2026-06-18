package com.professional.cam.ui.monitor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.ui.theme.DarkSurface
import com.professional.cam.ui.theme.HistogramBlue
import com.professional.cam.ui.theme.HistogramGreen
import com.professional.cam.ui.theme.HistogramLuminance
import com.professional.cam.ui.theme.HistogramRed

/**
 * 直方图视图
 *
 * 显示 RGB + 亮度直方图。
 * 位置：预览画面右上角
 * 大小：200x120dp
 */
@Composable
fun HistogramView(
    histogramData: AnalysisResult.HistogramData?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(200.dp)
            .height(120.dp)
            .background(DarkSurface.copy(alpha = 0.7f))
    ) {
        if (histogramData != null) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                drawHistogram(histogramData.luminance, HistogramLuminance, 0.5f)
                drawHistogram(histogramData.red, HistogramRed, 0.3f)
                drawHistogram(histogramData.green, HistogramGreen, 0.3f)
                drawHistogram(histogramData.blue, HistogramBlue, 0.3f)
            }
        }
    }
}

private fun DrawScope.drawHistogram(
    data: IntArray,
    color: Color,
    alpha: Float
) {
    if (data.isEmpty()) return

    val maxValue = data.max().coerceAtLeast(1)
    val width = size.width
    val height = size.height
    val barWidth = width / data.size

    val path = Path()
    path.moveTo(0f, height)

    for (i in data.indices) {
        val barHeight = (data[i].toFloat() / maxValue) * height
        val x = i * barWidth
        path.lineTo(x, height - barHeight)
    }

    path.lineTo(width, height)
    path.close()

    drawPath(path, color.copy(alpha = alpha))
}
