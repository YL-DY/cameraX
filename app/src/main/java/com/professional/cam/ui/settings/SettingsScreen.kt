package com.professional.cam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professional.cam.ui.theme.ControlActive
import com.professional.cam.ui.theme.DarkBackground
import com.professional.cam.ui.theme.DarkSurface
import com.professional.cam.ui.theme.DarkSurfaceVariant
import com.professional.cam.ui.theme.TextPrimary
import com.professional.cam.ui.theme.TextSecondary

/**
 * 设置界面
 *
 * 提供以下设置项：
 * - 视频分辨率
 * - 帧率
 * - 视频编码（H.264/H.265）
 * - 音频录制开关
 * - 防抖开关
 * - 存储位置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    var selectedResolution by remember { mutableStateOf("1080p FHD") }
    var selectedFps by remember { mutableStateOf("30 fps") }
    var selectedCodec by remember { mutableStateOf("H.264") }
    var enableAudio by remember { mutableStateOf(true) }
    var enableStabilization by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 视频分辨率
            SettingsDropdown(
                label = "Video Resolution",
                value = selectedResolution,
                options = listOf("4K UHD", "2K QHD", "1080p FHD", "720p HD"),
                onOptionSelected = { selectedResolution = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 帧率
            SettingsDropdown(
                label = "Frame Rate",
                value = selectedFps,
                options = listOf("24 fps", "25 fps", "30 fps", "48 fps", "50 fps", "60 fps"),
                onOptionSelected = { selectedFps = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 视频编码
            SettingsDropdown(
                label = "Video Codec",
                value = selectedCodec,
                options = listOf("H.264", "H.265 (HEVC)"),
                onOptionSelected = { selectedCodec = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 音频录制
            SettingsSwitch(
                label = "Audio Recording",
                description = "Record audio with video",
                checked = enableAudio,
                onCheckedChange = { enableAudio = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 防抖
            SettingsSwitch(
                label = "Video Stabilization",
                description = "Electronic Image Stabilization (EIS)",
                checked = enableStabilization,
                onCheckedChange = { enableStabilization = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 存储信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Storage",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Available storage will be displayed here",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                ) {
                    Text(
                        text = value,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ControlActive,
                    checkedTrackColor = ControlActive.copy(alpha = 0.5f)
                )
            )
        }
    }
}
