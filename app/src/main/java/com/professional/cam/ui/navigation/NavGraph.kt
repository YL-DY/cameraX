package com.professional.cam.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.professional.cam.ui.camera.CameraScreen
import com.professional.cam.ui.settings.SettingsScreen

/**
 * 导航图
 *
 * 应用导航结构：
 * - camera: 主相机界面
 * - settings: 设置界面
 */
object Routes {
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
}

@Composable
fun ProCamNavGraph(
    navController: NavHostController,
    cameraController: com.professional.cam.camera.manager.CameraController?
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CAMERA
    ) {
        composable(Routes.CAMERA) {
            CameraScreen(
                cameraController = cameraController,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
