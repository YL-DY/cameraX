package com.professional.cam.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professional.cam.ui.theme.DarkSurface
import com.professional.cam.ui.theme.ErrorColor
import com.professional.cam.ui.theme.SuccessColor
import com.professional.cam.ui.theme.TextPrimary
import com.professional.cam.ui.theme.TextSecondary
import com.professional.cam.ui.theme.WarningColor

/**
 * 系统监控面板
 *
 * 显示 CPU、内存、电池、温度、存储、录制时间。
 * 位置：屏幕顶部
 *
 * 注意：实际使用中，数据通过 SystemMonitor 的 currentValue StateFlow 获取。
 */
@Composable
fun SystemMonitorPanel(
    modifier: Modifier = Modifier,
    cpuUsage: String = "CPU: --%",
    memoryUsage: String = "RAM: --MB",
    batteryLevel: String = "Bat: --%",
    temperature: String = "Temp: --°C",
    storageInfo: String = "SD: --GB",
    recordingTime: String = "--:--"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface.copy(alpha = 0.8f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MonitorItem(text = cpuUsage)
        MonitorItem(text = memoryUsage)
        MonitorItem(text = batteryLevel)
        MonitorItem(text = temperature)
        MonitorItem(text = storageInfo)
        MonitorItem(
            text = recordingTime,
            isHighlighted = recordingTime != "--:--"
        )
    }
}

@Composable
private fun MonitorItem(
    text: String,
    isHighlighted: Boolean = false
) {
    Text(
        text = text,
        color = if (isHighlighted) SuccessColor else TextSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
