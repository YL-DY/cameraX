package com.professional.cam.data.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.professional.cam.core.util.DateTimeUtil
import com.professional.cam.core.util.FileUtil
import com.professional.cam.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频文件管理器
 *
 * 负责视频文件的创建、存储和管理。
 * 使用 MediaStore（Android 10+）或直接文件系统（Android 9-）存储。
 *
 * 设计原则：
 * - Android 10+ 使用 MediaStore（Scoped Storage）
 * - Android 9- 使用传统文件路径
 * - 自动处理文件名冲突
 * - 提供存储空间检查
 * - 支持缓存文件清理
 */
@Singleton
class VideoFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val VIDEO_DIRECTORY = "ProCam"
        private const val VIDEO_FILE_PREFIX = "VID_"
        private const val VIDEO_MIME_TYPE = "video/mp4"
        private const val THUMBNAIL_MIME_TYPE = "image/jpeg"
        private const val MIN_STORAGE_SPACE_BYTES = 500L * 1024 * 1024 // 500MB
    }

    /**
     * 创建视频输出文件
     *
     * @return 文件路径（String）或 null（存储空间不足）
     */
    fun createVideoFile(): String? {
        // 检查存储空间
        if (!hasEnoughStorage()) {
            Logger.e(Logger.Tag.RECORDING, "Insufficient storage space")
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createVideoFileMediaStore()
        } else {
            createVideoFileLegacy()
        }
    }

    /**
     * 将视频文件保存到 MediaStore（Android 10+）
     */
    fun saveVideoToMediaStore(sourceFile: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        return try {
            val fileName = DateTimeUtil.getRecordingFileName()
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$VIDEO_DIRECTORY")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                // 复制文件内容
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileOutputStream(outputStream.channel).use { fileOut ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(fileOut)
                        }
                    }
                }

                // 标记为已完成
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Logger.d(Logger.Tag.RECORDING, "Video saved to MediaStore: $uri")
            }

            uri
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to save video to MediaStore")
            null
        }
    }

    /**
     * 删除临时文件
     */
    fun deleteTempFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to delete temp file: $filePath")
            false
        }
    }

    /**
     * 获取视频目录
     */
    fun getVideoDirectory(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用缓存目录作为临时存储
            File(context.cacheDir, VIDEO_DIRECTORY).also { it.mkdirs() }
        } else {
            // Android 9- 使用 Movies 目录
            val moviesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            )
            File(moviesDir, VIDEO_DIRECTORY).also { it.mkdirs() }
        }
    }

    /**
     * 检查是否有足够的存储空间
     */
    fun hasEnoughStorage(): Boolean {
        return FileUtil.getAvailableStorageBytes() >= MIN_STORAGE_SPACE_BYTES
    }

    /**
     * 获取可用存储空间（字节）
     */
    fun getAvailableStorageBytes(): Long {
        return FileUtil.getAvailableStorageBytes()
    }

    /**
     * 清理缓存文件
     */
    fun cleanCache() {
        FileUtil.cleanCacheDir(context.cacheDir)
    }

    /**
     * 获取缓存文件大小
     */
    fun getCacheSize(): Long {
        return FileUtil.getDirectorySize(context.cacheDir)
    }

    // ── 私有方法 ──

    private fun createVideoFileMediaStore(): String? {
        return try {
            val fileName = DateTimeUtil.getRecordingFileName()
            val videoDir = File(context.cacheDir, VIDEO_DIRECTORY)
            if (!videoDir.exists()) {
                videoDir.mkdirs()
            }

            val file = File(videoDir, "$fileName.mp4")
            if (file.createNewFile()) {
                Logger.d(Logger.Tag.RECORDING, "Video file created: ${file.absolutePath}")
                file.absolutePath
            } else {
                // 文件名冲突，添加时间戳后缀
                val uniqueFile = File(videoDir, "${fileName}_${System.currentTimeMillis()}.mp4")
                if (uniqueFile.createNewFile()) {
                    uniqueFile.absolutePath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to create video file (MediaStore)")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun createVideoFileLegacy(): String? {
        return try {
            val fileName = DateTimeUtil.getRecordingFileName()
            val moviesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            )
            val videoDir = File(moviesDir, VIDEO_DIRECTORY)
            if (!videoDir.exists()) {
                videoDir.mkdirs()
            }

            val file = File(videoDir, "$fileName.mp4")
            if (file.createNewFile()) {
                Logger.d(Logger.Tag.RECORDING, "Video file created: ${file.absolutePath}")
                file.absolutePath
            } else {
                val uniqueFile = File(videoDir, "${fileName}_${System.currentTimeMillis()}.mp4")
                if (uniqueFile.createNewFile()) {
                    uniqueFile.absolutePath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.RECORDING, e, "Failed to create video file (Legacy)")
            null
        }
    }
}
