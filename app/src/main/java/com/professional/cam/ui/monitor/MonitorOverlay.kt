package com.professional.cam.ui.monitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.professional.cam.camera.analysis.AnalysisResult
import com.professional.cam.ui.monitor.components.HistogramView
import com.professional.cam.ui.monitor.components.PeakingOverlay
import com.professional.cam.ui.monitor.components.WaveformView
import com.professional.cam.ui.monitor.components.ZebraOverlay

/**
 * 专业监看叠加层
 *
 * 在预览画面上叠加显示：
 * - 直方图（右上角）
 * - 波形图（右下角）
 * - 斑马纹（过曝区域）
 * - 峰值对焦（合焦边缘）
 *
 * 注意：实际使用中，这些数据通过 FrameAnalyzer 的 result StateFlow 获取。
 */
@Composable
fun MonitorOverlay(
    modifier: Modifier = Modifier,
    histogramData: AnalysisResult.HistogramData? = null,
    waveformData: AnalysisResult.WaveformData? = null,
    zebraData: AnalysisResult.ZebraData? = null,
    peakingData: AnalysisResult.PeakingData? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 斑马纹叠加层（最底层）
        ZebraOverlay(
            zebraData = zebraData,
            modifier = Modifier.fillMaxSize()
        )

        // 峰值对焦叠加层
        PeakingOverlay(
            peakingData = peakingData,
            modifier = Modifier.fillMaxSize()
        )

        // 直方图（右上角）
        HistogramView(
            histogramData = histogramData,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )

        // 波形图（右下角）
        WaveformView(
            waveformData = waveformData,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )
    }
}
