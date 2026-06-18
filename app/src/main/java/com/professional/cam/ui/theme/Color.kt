package com.professional.cam.ui.theme

import androidx.compose.ui.graphics.Color

// ── 主色调 ──
val ProfessionalOrange = Color(0xFFFF6B35)
val ProfessionalOrangeDark = Color(0xFFCC552A)
val ProfessionalOrangeLight = Color(0xFFFF8C5E)

// ── 界面色调 ──
val DarkBackground = Color(0xFF0D0D0D)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurfaceVariant = Color(0xFF252525)
val DarkCard = Color(0xFF2A2A2A)

// ── 文字颜色 ──
val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF9E9E9E)
val TextDisabled = Color(0xFF616161)

// ── 专业监看颜色 ──
val HistogramRed = Color(0xFFFF4444)
val HistogramGreen = Color(0xFF44FF44)
val HistogramBlue = Color(0xFF4444FF)
val HistogramLuminance = Color(0xFFFFFFFF)

val WaveformColor = Color(0xFF88FF88)
val WaveformGrid = Color(0xFF335533)

val ZebraColor = Color(0xFFFF0000)
val ZebraAlpha = 0x66 // 40% 透明度

val PeakingColor = Color(0xFF00FF00)
val PeakingAlpha = 0x99 // 60% 透明度

// ── 系统监控颜色 ──
val WarningColor = Color(0xFFFFA000)
val ErrorColor = Color(0xFFEF5350)
val SuccessColor = Color(0xFF66BB6A)
val InfoColor = Color(0xFF42A5F5)

// ── 录制状态颜色 ──
val RecordingRed = Color(0xFFFF1744)
val RecordingRedDark = Color(0xFFD50000)

// ── 控制面板颜色 ──
val ControlActive = ProfessionalOrange
val ControlInactive = Color(0xFF555555)
val ControlBackground = Color(0xFF333333)
val ControlTrack = Color(0xFF444444)
val ControlTrackActive = ProfessionalOrange.copy(alpha = 0.5f)
