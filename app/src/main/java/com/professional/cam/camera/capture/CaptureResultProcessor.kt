package com.professional.cam.camera.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.professional.cam.core.util.DateTimeUtil
import com.professional.cam.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拍照结果处理器
 *
 * 职责：
 * - 将 JPEG 数据保存到 [MediaStore]（Android 10+ Scoped Storage）
 * - 写入 EXIF 元数据（时间、方向、分辨率）
 * - 自动生成文件名
 * - 保持后续 RAW 扩展能力
 *
 * 设计原则：
 * - 使用 [MediaStore] API 确保 Android 10+ 兼容性
 * - 不直接访问文件系统路径
 * - EXIF 信息在写入时同步设置
 */
@Singleton
class CaptureResultProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "CaptureResult"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private const val FILENAME_PREFIX = "IMG_"
        private const val FILENAME_SEPARATOR = "_"
        private const val FILENAME_EXTENSION = ".jpg"
        private const val RELATIVE_PATH = "DCIM/CameraPro"
    }

    /**
     * 保存 JPEG 照片到系统相册
     *
     * 使用 [MediaStore] API 写入，兼容 Android 10+ Scoped Storage。
     * 自动生成文件名，写入 EXIF 元数据。
     *
     * @param data JPEG 字节数据
     * @param width 图像宽度
     * @param height 图像高度
     * @param orientation JPEG 方向值（来自传感器方向）
     * @return 保存后的 [Uri]，失败返回 null
     */
    fun savePhoto(
        data: ByteArray,
        width: Int,
        height: Int,
        orientation: Int = 0
    ): Uri? {
        Logger.d(TAG, "Saving photo: ${width}x$height, ${data.size} bytes")

        try {
            val fileName = generateFileName()
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME_TYPE)
                put(MediaStore.Images.Media.WIDTH, width)
                put(MediaStore.Images.Media.HEIGHT, height)
                put(MediaStore.Images.Media.ORIENTATION, orientation)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val imageCollectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(imageCollectionUri, contentValues)
            if (uri == null) {
                Logger.e(TAG, "Failed to create MediaStore entry")
                return null
            }

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(data)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                Logger.e(TAG, e, "Failed to write image data to MediaStore")
                // 写入失败时删除 MediaStore 条目
                resolver.delete(uri, null, null)
                return null
            }

            // 写入 EXIF 元数据
            try {
                writeExifMetadata(uri, data, orientation)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to write EXIF metadata: ${e.message}")
                // EXIF 写入失败不影响主流程
            }

            // Android 10+ 标记为非待处理
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }

            Logger.d(TAG, "Photo saved successfully: $uri")
            return uri

        } catch (e: Exception) {
            Logger.e(TAG, e, "Failed to save photo")
            return null
        }
    }

    /**
     * 生成文件名
     *
     * 格式：IMG_yyyyMMdd_HHmmss_SSS.jpg
     */
    private fun generateFileName(): String {
        val timestamp = DateTimeUtil.getRecordingFileName()
        return FILENAME_PREFIX + timestamp + FILENAME_EXTENSION
    }

    /**
     * 写入 EXIF 元数据
     *
     * 使用 [ExifInterface] 写入以下信息：
     * - 拍摄时间
     * - 图像方向
     * - 图像宽度/高度
     *
     * @param uri MediaStore URI
     * @param data JPEG 字节数据
     * @param orientation 方向值
     */
    private fun writeExifMetadata(
        uri: Uri,
        data: ByteArray,
        orientation: Int
    ) {
        try {
            // 通过 ContentResolver 打开文件描述符写入 EXIF
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)

                // 拍摄时间（当前时间）
                val dateTime = getCurrentExifDateTime()
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)

                // 方向
                val exifOrientation = when (orientation) {
                    0 -> ExifInterface.ORIENTATION_NORMAL
                    90 -> ExifInterface.ORIENTATION_ROTATE_90
                    180 -> ExifInterface.ORIENTATION_ROTATE_180
                    270 -> ExifInterface.ORIENTATION_ROTATE_270
                    else -> ExifInterface.ORIENTATION_NORMAL
                }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())

                // 图像宽度/高度
                exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, getImageWidth(data).toString())
                exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, getImageHeight(data).toString())

                // 制造商和型号（可选）
                exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
                exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)

                exif.saveAttributes()
                Logger.d(TAG, "EXIF metadata written: date=$dateTime, orientation=$orientation")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Could not write EXIF via FileDescriptor, trying fallback")
            // 回退方案：写入临时文件再读取
            writeExifViaTempFile(uri, data, orientation)
        }
    }

    /**
     * 通过临时文件写入 EXIF（回退方案）
     */
    private fun writeExifViaTempFile(
        uri: Uri,
        data: ByteArray,
        orientation: Int
    ) {
        try {
            val tempFile = File(context.cacheDir, "temp_exif_${System.nanoTime()}.jpg")
            try {
                tempFile.outputStream().use { it.write(data) }

                val exif = ExifInterface(tempFile.absolutePath)
                val dateTime = getCurrentExifDateTime()
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)

                val exifOrientation = when (orientation) {
                    0 -> ExifInterface.ORIENTATION_NORMAL
                    90 -> ExifInterface.ORIENTATION_ROTATE_90
                    180 -> ExifInterface.ORIENTATION_ROTATE_180
                    270 -> ExifInterface.ORIENTATION_ROTATE_270
                    else -> ExifInterface.ORIENTATION_NORMAL
                }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, getImageWidth(data).toString())
                exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, getImageHeight(data).toString())
                exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
                exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
                exif.saveAttributes()

                // 将带 EXIF 的数据写回 MediaStore
                val updatedData = tempFile.readBytes()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(updatedData)
                    outputStream.flush()
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e, "Fallback EXIF write also failed")
        }
    }

    /**
     * 从 JPEG 数据中获取图像宽度
     */
    private fun getImageWidth(data: ByteArray): Int {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            options.outWidth
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 从 JPEG 数据中获取图像高度
     */
    private fun getImageHeight(data: ByteArray): Int {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            options.outHeight
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取当前时间的 EXIF 格式字符串
     *
     * 格式：yyyy:MM:dd HH:mm:ss
     */
    private fun getCurrentExifDateTime(): String {
        val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }
}
