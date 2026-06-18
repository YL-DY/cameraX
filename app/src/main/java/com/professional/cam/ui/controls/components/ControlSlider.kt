package com.professional.cam.ui.controls.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professional.cam.ui.theme.ControlActive
import com.professional.cam.ui.theme.ControlInactive
import com.professional.cam.ui.theme.ControlTrack
import com.professional.cam.ui.theme.ControlTrackActive
import com.professional.cam.ui.theme.TextPrimary
import com.professional.cam.ui.theme.TextSecondary

/**
 * 控制滑块组件
 *
 * 用于 ISO、快门、对焦等参数调节。
 * 显示当前值和标签。
 */
@Composable
fun ControlSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标签
        Text(
            text = label,
            color = if (enabled) TextSecondary else ControlInactive,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 滑块
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) ControlActive else ControlInactive,
                activeTrackColor = ControlTrackActive,
                inactiveTrackColor = ControlTrack,
                disabledThumbColor = ControlInactive,
                disabledActiveTrackColor = ControlInactive.copy(alpha = 0.3f),
                disabledInactiveTrackColor = ControlTrack.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 当前值
        Text(
            text = valueText,
            color = if (enabled) TextPrimary else ControlInactive,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}
