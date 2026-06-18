package com.professional.cam.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限管理器
 *
 * 统一管理应用所需的所有运行时权限。
 * 职责：
 * 1. 检查权限是否已授予
 * 2. 提供需要请求的权限列表
 * 3. 判断 Android 版本差异
 */
@Singleton
class PermissionManager @Inject constructor(
    private val context: Context
) {

    /**
     * 录像所需的所有权限
     */
    val recordingPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    /**
     * 检查所有录像权限是否已授予
     */
    fun hasAllRecordingPermissions(): Boolean {
        return recordingPermissions.all { isPermissionGranted(it) }
    }

    /**
     * 检查单个权限是否已授予
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查相机权限
     */
    fun hasCameraPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.CAMERA)
    }

    /**
     * 检查录音权限
     */
    fun hasAudioPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * 检查通知权限 (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * 获取未授予的权限列表
     */
    fun getMissingPermissions(permissions: List<String>): List<String> {
        return permissions.filter { !isPermissionGranted(it) }
    }
}
