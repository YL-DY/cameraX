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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.ui.theme.DarkSurface
import com.professional.cam.ui.theme.WaveformColor
import com.professional.cam.ui.theme.WaveformGrid

/**
 * 波形图视图
 *
 * 显示亮度波形图。
 * 位置：预览画面右下角
 * 大小：200x80dp
 */
@Composable
fun WaveformView(
    waveformData: AnalysisResult.WaveformData?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(240.dp)
            .height(80.dp)
            .background(DarkSurface.copy(alpha = 0.7f))
    ) {
        if (waveformData != null) {
            Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                val w = size.width
                val h = size.height

                // 绘制网格线
                for (i in 0..3) {
                    val y = h * i / 4
                    drawLine(
                        color = WaveformGrid,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 0.5f
                    )
                }

                // 绘制波形数据
                val luminance = waveformData.luminance
                val waveWidth = waveformData.width
                val waveHeight = waveformData.height

                if (luminance.isNotEmpty() && waveWidth > 0) {
                    val pixelWidth = w / waveWidth
                    val pixelHeight = h / waveHeight

                    for (y in 0 until waveHeight) {
                        for (x in 0 until waveWidth) {
                            val index = y * waveWidth + x
                            if (index < luminance.size && luminance[index] > 0f) {
                                drawRect(
                                    color = WaveformColor.copy(alpha = luminance[index]),
                                    topLeft = Offset(x * pixelWidth, h - (y * pixelHeight)),
                                    size = androidx.compose.ui.geometry.Size(
                                        pixelWidth.coerceAtLeast(1f),
                                        pixelHeight.coerceAtLeast(1f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
