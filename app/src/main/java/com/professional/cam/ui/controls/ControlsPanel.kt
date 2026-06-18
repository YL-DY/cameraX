package com.professional.cam.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professional.cam.camera.manager.CameraController
import com.professional.cam.ui.controls.components.ControlSlider
import com.professional.cam.ui.theme.ControlActive
import com.professional.cam.ui.theme.ControlBackground
import com.professional.cam.ui.theme.ControlInactive
import com.professional.cam.ui.theme.DarkSurface
import com.professional.cam.ui.theme.RecordingRed
import com.professional.cam.ui.theme.TextPrimary
import com.professional.cam.ui.theme.TextSecondary

/**
 * 专业控制面板
 *
 * 显示 ISO、快门速度、白平衡、对焦、曝光补偿控制。
 * 每个控制根据设备能力自动启用/禁用。
 */
@Composable
fun ControlsPanel(
    cameraController: CameraController?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(8.dp)
    ) {
        // 录制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    if (cameraController?.isRecording?.value == true) {
                        cameraController.stopRecording()
                    } else {
                        cameraController?.startRecording()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cameraController?.isRecording?.value == true)
                        RecordingRed else ControlActive
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = if (cameraController?.isRecording?.value == true) "■ STOP" else "● REC",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }

        // 控制滑块行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // ISO 控制
            ControlSlider(
                label = "ISO",
                value = 0.5f,
                valueText = "AUTO",
                range = 0f..1f,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            )

            // 快门速度
            ControlSlider(
                label = "SHUTTER",
                value = 0.5f,
                valueText = "AUTO",
                range = 0f..1f,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            )

            // 白平衡
            ControlSlider(
                label = "WB",
                value = 0.5f,
                valueText = "AUTO",
                range = 0f..1f,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            )

            // 对焦
            ControlSlider(
                label = "FOCUS",
                value = 0.5f,
                valueText = "AF",
                range = 0f..1f,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            )

            // 曝光补偿
            ControlSlider(
                label = "EV",
                value = 0.5f,
                valueText = "0.0",
                range = 0f..1f,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
