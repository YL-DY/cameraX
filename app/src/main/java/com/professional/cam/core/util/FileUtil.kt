package com.professional.cam.core.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * 文件系统工具类
 */
object FileUtil {

    /**
     * 获取应用录像缓存目录
     */
    fun getRecordingCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 获取外部存储录像目录
     */
    fun getRecordingPublicDir(): File {
        val dir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        ).resolve("ProCam")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 获取可用存储空间（字节）
     */
    fun getAvailableStorageBytes(path: File = Environment.getExternalStorageDirectory()): Long {
        return try {
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            blockSize * availableBlocks
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    /**
     * 获取可用存储空间（可读格式）
     */
    fun getAvailableStorageFormatted(path: File = Environment.getExternalStorageDirectory()): String {
        val bytes = getAvailableStorageBytes(path)
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }

    /**
     * 检查是否有足够的存储空间
     * @param requiredBytes 所需字节数
     */
    fun hasEnoughStorage(requiredBytes: Long = 500 * 1024 * 1024): Boolean {
        return getAvailableStorageBytes() >= requiredBytes
    }

    /**
     * 清理缓存文件
     */
    fun cleanCache(context: Context): Long {
        val cacheDir = getRecordingCacheDir(context)
        var deletedBytes = 0L
        cacheDir.listFiles()?.forEach { file ->
            deletedBytes += file.length()
            file.delete()
        }
        return deletedBytes
    }

    /**
     * 生成唯一的录像文件名
     */
    fun generateVideoFileName(extension: String = "mp4"): String {
        val timestamp = DateTimeUtil.getRecordingFileName("VID")
        return "$timestamp.$extension"
    }

    /**
     * 获取文件大小（可读格式）
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}
