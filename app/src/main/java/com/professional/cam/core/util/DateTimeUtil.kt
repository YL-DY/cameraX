package com.professional.cam.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 日期时间工具类
 */
object DateTimeUtil {

    private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
    private const val DISPLAY_FORMAT = "HH:mm:ss"
    private const val DATE_DISPLAY_FORMAT = "yyyy/MM/dd HH:mm:ss"

    /**
     * 生成文件名时间戳
     */
    fun getFilenameTimestamp(): String {
        val sdf = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date())
    }

    /**
     * 格式化录像时长
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 格式化显示时间
     */
    fun getDisplayTime(): String {
        val sdf = SimpleDateFormat(DISPLAY_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 格式化日期显示
     */
    fun getDateDisplayTime(): String {
        val sdf = SimpleDateFormat(DATE_DISPLAY_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 获取录像文件名（不含扩展名）
     */
    fun getRecordingFileName(prefix: String = "VID"): String {
        return "${prefix}_${getFilenameTimestamp()}"
    }
}
