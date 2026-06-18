package com.professional.cam.ui.camera

import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.professional.cam.camera.manager.CameraController
import com.professional.cam.camera.manager.CameraState
import com.professional.cam.ui.camera.components.ViewFinder
import com.professional.cam.ui.theme.DarkBackground
import com.professional.cam.ui.theme.TextSecondary

/**
 * 相机主界面
 *
 * 职责：
 * - 创建并管理相机预览取景器（[ViewFinder]）
 * - 在 [ViewFinder] 的 Surface 就绪后自动初始化相机并启动预览
 * - 在 Composable 离开时自动释放相机资源
 * - 显示相机状态指示
 * - 提供设置导航入口
 *
 * 生命周期管理：
 * - [ViewFinder] 的 [android.view.SurfaceHolder.Callback] 驱动相机启动/停止
 * - [DisposableEffect] 确保离开页面时释放所有相机资源
 * - 屏幕旋转通过 Activity 重建触发 Compose 重组，[ViewFinder] 重新创建
 *   Surface 后自动重新启动预览
 *
 * 调用链：
 * ```
 * Surface 创建 → controller.attachPreviewSurface(surface)
 *             → controller.openCameraIfNeeded()
 *             → (内部) openCamera() → createSessionAndStartPreview()
 *
 * Surface 销毁 → controller.detachPreviewSurface()
 *
 * 页面离开   → controller.stopPreview() + controller.release()
 * ```
 *
 * @param cameraController 相机控制器（由 DI 注入）
 * @param onNavigateToSettings 导航到设置页面的回调
 */
@Composable
fun CameraScreen(
    cameraController: CameraController?,
    onNavigateToSettings: () -> Unit = {}
) {
    val cameraState = cameraController?.state?.collectAsState()?.value ?: CameraState.Idle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 相机预览取景器
        // Surface 就绪后自动启动相机预览
        ViewFinder(
            onSurfaceAvailable = { surface ->
                cameraController?.attachPreviewSurface(surface)
                cameraController?.openCameraIfNeeded()
            },
            onSurfaceDestroyed = {
                cameraController?.detachPreviewSurface()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 设置按钮
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary
            )
        }

        // 相机状态指示
        Text(
            text = when (cameraState) {
                is CameraState.Idle -> "Idle"
                is CameraState.Initialized -> "Initialized"
                is CameraState.Opening -> "Opening..."
                is CameraState.Opened -> "Camera Ready"
                is CameraState.Previewing -> "Preview Active"
                is CameraState.Closing -> "Closing..."
                is CameraState.SwitchingCamera -> "Switching..."
                is CameraState.Error -> "Error: ${cameraState.message}"
            },
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // ── 生命周期管理 ──

    // 离开页面时：释放所有相机资源
    DisposableEffect(Unit) {
        onDispose {
            cameraController?.let { controller ->
                controller.stopPreview()
                controller.release()
            }
        }
    }
}
