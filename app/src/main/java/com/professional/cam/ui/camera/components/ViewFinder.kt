package com.professional.cam.ui.camera.components

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.professional.cam.core.util.Logger

/**
 * 取景器组件
 *
 * 使用 [SurfaceView] 提供相机预览 Surface。
 * 职责单一：仅负责 Surface 的创建与销毁，不参与任何相机控制逻辑。
 *
 * 设计原则：
 * - 通过 [onSurfaceAvailable] 和 [onSurfaceDestroyed] 回调将 Surface
 *   生命周期通知给 [com.professional.cam.camera.manager.CameraController]
 * - [com.professional.cam.camera.manager.CameraController] 只依赖 [Surface]，
 *   不依赖此 View 的具体实现
 * - 未来可替换为 [android.view.TextureView] 或其他渲染方案，无需修改
 *   [com.professional.cam.camera.manager.CameraController]
 *
 * Surface 生命周期：
 * ```
 * surfaceCreated(SurfaceHolder) → surface 可用 → 通知 CameraController
 * surfaceChanged(SurfaceHolder, format, width, height) → 尺寸变化 → 重新配置
 * surfaceDestroyed(SurfaceHolder) → surface 销毁 → 通知 CameraController
 * ```
 *
 * @param onSurfaceAvailable Surface 创建完成回调，参数为可用的 [Surface]
 * @param onSurfaceDestroyed Surface 销毁回调，用于通知停止预览
 * @param modifier Compose 修饰符
 */
@Composable
fun ViewFinder(
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val surfaceView = remember {
        SurfaceView(context).apply {
            // 保持屏幕常亮，防止预览时息屏
            keepScreenOn = true
        }
    }

    // 注册 SurfaceHolder.Callback 监听 Surface 生命周期
    DisposableEffect(Unit) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Logger.d(Logger.Tag.CAMERA, "ViewFinder surface created")
                onSurfaceAvailable(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Logger.d(
                    Logger.Tag.CAMERA,
                    "ViewFinder surface changed: ${width}x$height"
                )
                // Surface 尺寸变化时，重新通知 CameraController
                // CameraController 会重新创建 CaptureSession 以适应新尺寸
                onSurfaceAvailable(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Logger.d(Logger.Tag.CAMERA, "ViewFinder surface destroyed")
                onSurfaceDestroyed()
            }
        }

        surfaceView.holder.addCallback(callback)

        onDispose {
            surfaceView.holder.removeCallback(callback)
            Logger.d(Logger.Tag.CAMERA, "ViewFinder DisposableEffect disposed")
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier
    )
}
