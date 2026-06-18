package com.professional.cam.core.extension

import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

/**
 * 文件操作扩展函数
 */

fun File.ensureDirExists(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

fun File.safeWrite(block: (FileOutputStream) -> Unit): Result<File> {
    return try {
        FileOutputStream(this).use { outputStream ->
            block(outputStream)
            outputStream.flush()
        }
        Result.success(this)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

fun File.toContentUri(context: Context): Uri? {
    return try {
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension) ?: "video/mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, 
                Environment.DIRECTORY_MOVIES + "/ProCam")
        }

        context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    } catch (e: Exception) {
        null
    }
}

fun File.getMimeType(): String {
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

fun File.sizeFormatted(): String {
    val bytes = length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}
