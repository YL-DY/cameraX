package com.professional.cam.camera.manager

import android.hardware.camera2.CaptureResult
import android.view.Surface
import com.professional.cam.camera.capability.CameraCapabilities
import com.professional.cam.camera.capability.CameraCapability
import com.professional.cam.camera.capability.CapabilityDetector
import com.professional.cam.camera.capture.PhotoResult
import com.professional.cam.camera.config.CameraConfig
import com.professional.cam.camera.config.CameraSettings
import com.professional.cam.camera.config.ExposureMode
import com.professional.cam.camera.config.FlashMode
import com.professional.cam.camera.config.FocusMode
import com.professional.cam.camera.config.FpsConfig
import com.professional.cam.camera.config.ResolutionConfig
import com.professional.cam.camera.config.WhiteBalanceMode
import com.professional.cam.camera.recorder.RecorderState
import com.professional.cam.core.error.AppError
import com.professional.cam.core.error.ErrorHandler
import com.professional.cam.core.error.ErrorRecoveryManager
import com.professional.cam.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 统一相机控制器
 *
 * UI 层唯一交互对象。封装所有相机基础操作：
 * - 生命周期管理（初始化/打开/关闭/预览/释放）
 * - 镜头切换
 * - 相机信息查询
 *
 * 设计原则：
 * - UI 层不允许直接操作 Camera2 API
 * - 所有控制通过此控制器统一调度
 * - [Camera2Engine] 是 [android.hardware.camera2.CameraDevice]、
 *   [android.hardware.camera2.CameraCaptureSession] 和
 *   [android.hardware.camera2.CaptureRequest] 的唯一管理者
 * - 此控制器仅负责协调调用顺序和生命周期管理
 * - 不持有核心相机对象，避免职责混乱
 *
 * 状态流转：
 * ```
 * Idle → Initialized → Opening → Opened → Previewing → Closing → Idle
 *                    ↘  Error  ↙
 * ```
 */
class CameraController @Inject constructor(
    private val camera2Engine: Camera2Engine,
    private val capabilityDetector: CapabilityDetector,
    private val errorHandler: ErrorHandler,
    private val errorRecoveryManager: ErrorRecoveryManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── 状态 ──

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _currentCameraId = MutableStateFlow("0")
    val currentCameraId: StateFlow<String> = _currentCameraId.asStateFlow()

    // ── 能力缓存 ──

    private var currentCapabilities: CameraCapabilities? = null
    val capabilities: CameraCapabilities? get() = currentCapabilities

    // ── 统一能力模型（CameraCapability） ──

    private var cameraCapability: CameraCapability? = null

    // ── 配置 ──

    private var currentConfig: CameraConfig = CameraConfig()

    // ── 统一参数模型（Single Source of Truth） ──

    private val _settings = MutableStateFlow(CameraSettings.DEFAULT)
    val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    // ── 捕获结果回调（供后续专业控制使用） ──

    private var onCaptureResultCallback: ((CaptureResult) -> Unit)? = null

    // ── 内部状态跟踪 ──

    /** 是否已调用 [initialize] */
    private var isInitialized = false

    /** 是否已调用 [openCamera] 且 CameraDevice 已就绪 */
    private var isCameraOpened = false

    /** 是否预览已激活 */
    private var isPreviewActive = false

    /** 当前预览 Surface */
    private var currentSurface: Surface? = null

    /**
     * 初始化相机
     *
     * 1. 初始化 Camera2Engine（CameraManager + HandlerThread）
     * 2. 检测指定相机的能力
     * 3. 缓存能力信息
     * 4. 设置捕获结果回调转发
     *
     * 多次调用安全，已初始化时直接返回。
     *
     * @param cameraId 相机 ID，默认 "0"（后置）
     */
    fun initialize(cameraId: String = "0") {
        if (isInitialized) {
            Logger.d(Logger.Tag.CAMERA, "CameraController already initialized")
            return
        }

        Logger.d(Logger.Tag.CAMERA, "CameraController initializing with camera: $cameraId")

        try {
            // 1. 初始化 Camera2Engine
            camera2Engine.initialize()

            // 2. 检测相机能力
            _currentCameraId.value = cameraId
            currentCapabilities = capabilityDetector.getCapabilities(cameraId)
                ?: capabilityDetector.detectCamera(cameraId)

            // 3. 构建统一 CameraCapability 并设置到 Camera2Engine
            currentCapabilities?.let { caps ->
                val characteristics = capabilityDetector.getCharacteristics(cameraId)
                characteristics?.let { chars ->
                    cameraCapability = CameraCapability.fromCharacteristics(chars)
                    cameraCapability?.let { camera2Engine.setCameraCapability(it) }
                }
            }

            // 4. 设置捕获结果回调转发
            camera2Engine.setOnCaptureResultCallback { result ->
                onCaptureResultCallback?.invoke(result)
            }

            // 5. 重置设置为默认值
            _settings.value = CameraSettings.DEFAULT

            isInitialized = true
            _state.value = CameraState.Initialized
            Logger.d(Logger.Tag.CAMERA, "CameraController initialized: $cameraId")
        } catch (e: Exception) {
            val result = errorHandler.handle(
                AppError.CameraError.InitializationFailed(e.message ?: "Unknown")
            )
            Logger.e(Logger.Tag.CAMERA, e, result.userMessage)
            _state.value = CameraState.Error(result.userMessage)
        }
    }

    /**
     * 打开相机设备
     *
     * 异步操作，通过 [state] Flow 监听状态变化：
     * - [CameraState.Opening]：正在打开
     * - [CameraState.Opened]：已打开，CameraDevice 就绪
     * - [CameraState.Error]：打开失败
     *
     * 必须先调用 [initialize]。
     * 如果相机已打开，先关闭再重新打开。
     *
     * @param cameraId 相机 ID，默认使用当前相机
     */
    fun openCamera(cameraId: String = _currentCameraId.value) {
        if (!isInitialized) {
            Logger.w(Logger.Tag.CAMERA, "Cannot open camera: not initialized")
            val result = errorHandler.handle(AppError.CameraError.NotInitialized)
            _state.value = CameraState.Error(result.userMessage)
            return
        }

        val targetCameraId = cameraId
        _currentCameraId.value = targetCameraId
        currentConfig = currentConfig.copy(cameraId = targetCameraId)
        isCameraOpened = false
        isPreviewActive = false

        Logger.d(Logger.Tag.CAMERA, "Opening camera: $targetCameraId")
        _state.value = CameraState.Opening

        camera2Engine.openCamera(
            config = currentConfig,
            onStateChanged = { engineState ->
                when (engineState) {
                    is CameraEngineState.Opened -> {
                        isCameraOpened = true
                        _state.value = CameraState.Opened
                        Logger.d(Logger.Tag.CAMERA, "Camera opened: $targetCameraId")

                        // 如果已有 Surface，自动创建 CaptureSession 并启动预览
                        currentSurface?.let { surface ->
                            createSessionAndStartPreview(surface)
                        }
                    }
                    is CameraEngineState.Error -> {
                        val result = errorHandler.handle(
                            AppError.CameraError.OpenFailed(targetCameraId, engineState.message)
                        )
                        _state.value = CameraState.Error(result.userMessage)
                    }
                    is CameraEngineState.Disconnected -> {
                        val result = errorHandler.handle(
                            AppError.CameraError.CameraDisconnected(targetCameraId)
                        )
                        _state.value = CameraState.Error(result.userMessage)
                    }
                    else -> { /* Opening, Closed, SessionCreated, PreviewActive 忽略 */ }
                }
            }
        )
    }

    /**
     * 附加预览 Surface
     *
     * 由 [com.professional.cam.ui.camera.CameraScreen] 在 Surface 就绪时调用。
     * 保存 Surface 引用，如果相机已打开则自动创建 CaptureSession 并启动预览。
     *
     * @param surface 预览 Surface（来自 [android.view.SurfaceView]）
     */
    fun attachPreviewSurface(surface: Surface) {
        Logger.d(Logger.Tag.CAMERA, "attachPreviewSurface called")

        // 如果 Surface 相同且预览已激活，跳过
        if (currentSurface == surface && isPreviewActive) {
            Logger.d(Logger.Tag.CAMERA, "Preview already active with same surface")
            return
        }

        currentSurface = surface

        // 如果相机已打开，直接创建 Session 并启动预览
        if (isCameraOpened) {
            createSessionAndStartPreview(surface)
        }
    }

    /**
     * 分离预览 Surface
     *
     * 由 [com.professional.cam.ui.camera.CameraScreen] 在 Surface 销毁时调用。
     * 清除 Surface 引用，停止预览。
     */
    fun detachPreviewSurface() {
        Logger.d(Logger.Tag.CAMERA, "detachPreviewSurface called")
        currentSurface = null
        stopPreview()
    }

    /**
     * 按需打开相机
     *
     * 如果相机尚未打开且已初始化，则打开相机。
     * 如果尚未初始化，先初始化再打开。
     * 打开后如果已有 Surface，自动创建 CaptureSession 并启动预览。
     */
    fun openCameraIfNeeded() {
        Logger.d(Logger.Tag.CAMERA, "openCameraIfNeeded called")

        // 1. 确保已初始化
        if (!isInitialized) {
            Logger.d(Logger.Tag.CAMERA, "Not initialized, initializing first")
            initialize()
        }

        // 2. 如果相机未打开，打开相机（打开后会自动创建 Session）
        if (!isCameraOpened) {
            Logger.d(Logger.Tag.CAMERA, "Camera not opened, opening now")
            openCamera()
        }
    }

    /**
     * 创建 Capture Session 并启动预览
     *
     * @param surface 预览 Surface
     */
    private fun createSessionAndStartPreview(surface: Surface) {
        Logger.d(Logger.Tag.CAMERA, "Creating capture session and starting preview")

        camera2Engine.createCaptureSession(
            surface = surface,
            onStateChanged = { engineState ->
                when (engineState) {
                    is CameraEngineState.PreviewActive -> {
                        isPreviewActive = true
                        _state.value = CameraState.Previewing
                        Logger.d(Logger.Tag.CAMERA, "Preview active")
                    }
                    is CameraEngineState.Error -> {
                        val result = errorHandler.handle(
                            AppError.CameraError.SessionCreationFailed(engineState.message)
                        )
                        _state.value = CameraState.Error(result.userMessage)
                    }
                    else -> { /* SessionCreated 等中间状态忽略 */ }
                }
            }
        )
    }

    /**
     * 停止预览
     *
     * 按顺序停止：
     * 1. Camera2 关闭（abortCaptures → close session → close device）
     * 2. 更新状态为 [CameraState.Idle]
     */
    fun stopPreview() {
        if (!isPreviewActive && !isCameraOpened) {
            Logger.d(Logger.Tag.CAMERA, "Preview already stopped")
            return
        }

        Logger.d(Logger.Tag.CAMERA, "Stopping preview")
        _state.value = CameraState.Closing

        try {
            // 关闭 Camera2（内部会 abortCaptures → close session → close device）
            camera2Engine.close()
            isCameraOpened = false
            isPreviewActive = false

            _state.value = CameraState.Idle
            Logger.d(Logger.Tag.CAMERA, "Preview stopped")
        } catch (e: Exception) {
            val result = errorHandler.handle(
                AppError.CameraError.CloseFailed(e.message ?: "Unknown")
            )
            Logger.e(Logger.Tag.CAMERA, e, result.userMessage)
            isCameraOpened = false
            isPreviewActive = false
            _state.value = CameraState.Idle
        }
    }

    /**
     * 释放所有资源
     *
     * 包括 Camera2Engine 的完整释放。
     * 调用后需要重新 [initialize] 才能再次使用。
     */
    fun release() {
        Logger.d(Logger.Tag.CAMERA, "Releasing CameraController")

        try {
            // 1. 清理回调
            camera2Engine.setOnCaptureResultCallback(null)
            onCaptureResultCallback = null

            // 2. 停止预览并关闭相机
            if (isPreviewActive || isCameraOpened) {
                camera2Engine.close()
                isCameraOpened = false
                isPreviewActive = false
            }

            // 3. 释放 Camera2Engine（停止 HandlerThread）
            camera2Engine.release()

            // 4. 清理缓存
            currentCapabilities = null
            currentSurface = null
            isInitialized = false

            _state.value = CameraState.Idle
            Logger.d(Logger.Tag.CAMERA, "CameraController released")
        } catch (e: Exception) {
            val result = errorHandler.handle(
                AppError.CameraError.ReleaseFailed(e.message ?: "Unknown")
            )
            Logger.e(Logger.Tag.CAMERA, e, result.userMessage)
            isInitialized = false
            isCameraOpened = false
            isPreviewActive = false
            _state.value = CameraState.Idle
        }
    }

    /**
     * 切换相机
     *
     * 1. 关闭当前相机
     * 2. 初始化新相机
     * 3. 重新启动预览（需要外部再次调用 [startPreviewWithSurface]）
     *
     * @param cameraId 目标相机 ID
     */
    fun switchCamera(cameraId: String) {
        if (cameraId == _currentCameraId.value) {
            Logger.d(Logger.Tag.CAMERA, "Already on camera: $cameraId")
            return
        }

        Logger.d(Logger.Tag.CAMERA, "Switching camera from ${_currentCameraId.value} to $cameraId")

        try {
            // 1. 关闭当前相机
            if (isPreviewActive || isCameraOpened) {
                camera2Engine.close()
                isCameraOpened = false
                isPreviewActive = false
            }

            // 2. 初始化新相机
            _currentCameraId.value = cameraId
            currentCapabilities = capabilityDetector.getCapabilities(cameraId)
                ?: capabilityDetector.detectCamera(cameraId)

            _state.value = CameraState.SwitchingCamera
            Logger.d(
                Logger.Tag.CAMERA,
                "Camera switched to: $cameraId, call startPreviewWithSurface() to begin preview"
            )
        } catch (e: Exception) {
            val result = errorHandler.handle(
                AppError.CameraError.SwitchFailed(cameraId, e.message ?: "Unknown")
            )
            Logger.e(Logger.Tag.CAMERA, e, result.userMessage)
            _state.value = CameraState.Error(result.userMessage)
        }
    }

    /**
     * 设置捕获结果回调
     *
     * 用于后续专业控制模块获取每帧的 CaptureResult。
     *
     * @param callback 捕获结果回调，传 null 取消
     */
    fun setOnCaptureResultCallback(callback: ((CaptureResult) -> Unit)?) {
        onCaptureResultCallback = callback
    }

    // ── 统一控制接口 ──
    //
    // 所有参数修改遵循以下流程：
    // 1. 更新 _settings（Single Source of Truth）
    // 2. 调用 camera2Engine.applySettings() 应用新参数
    // 3. 不重建 CameraSession，不中断预览

    /**
     * 批量更新相机设置
     *
     * 直接传入完整 [CameraSettings] 对象，适用于需要同时修改多个参数的场景。
     * 内部会进行参数校验并调用 [Camera2Engine.applySettings]。
     *
     * @param settings 新的相机设置
     */
    fun updateSettings(settings: CameraSettings) {
        Logger.d(Logger.Tag.CAMERA, "updateSettings: $settings")
        _settings.value = settings
        camera2Engine.applySettings(settings)
    }

    /**
     * 更新 ISO 值
     *
     * 自动将曝光模式切换为 [ExposureMode.MANUAL_ISO]。
     * 如果当前已是 [ExposureMode.MANUAL] 则保持。
     *
     * @param iso ISO 值，范围由 [CameraCapability.isoRange] 决定
     */
    fun updateIso(iso: Int) {
        Logger.d(Logger.Tag.CAMERA, "updateIso: $iso")
        val current = _settings.value
        val newMode = when (current.exposureMode) {
            ExposureMode.MANUAL -> ExposureMode.MANUAL
            else -> ExposureMode.MANUAL_ISO
        }
        val newSettings = current.copy(
            exposureMode = newMode,
            iso = iso
        )
        _settings.value = newSettings
        camera2Engine.applySettings(newSettings)
    }

    /**
     * 更新曝光时间（快门速度）
     *
     * 自动将曝光模式切换为 [ExposureMode.MANUAL_SHUTTER]。
     * 如果当前已是 [ExposureMode.MANUAL] 则保持。
     *
     * @param time 曝光时间（纳秒），范围由 [CameraCapability.exposureTimeRange] 决定
     */
    fun updateExposureTime(time: Long) {
        Logger.d(Logger.Tag.CAMERA, "updateExposureTime: $time")
        val current = _settings.value
        val newMode = when (current.exposureMode) {
            ExposureMode.MANUAL -> ExposureMode.MANUAL
            else -> ExposureMode.MANUAL_SHUTTER
        }
        val newSettings = current.copy(
            exposureMode = newMode,
            exposureTime = time
        )
        _settings.value = newSettings
        camera2Engine.applySettings(newSettings)
    }

    /**
     * 更新对焦距离
     *
     * 自动将对焦模式切换为 [FocusMode.MANUAL]。
     *
     * @param distance 对焦距离，范围由 [CameraCapability.focusDistanceRange] 决定
     */
    fun updateFocusDistance(distance: Float) {
        Logger.d(Logger.Tag.CAMERA, "updateFocusDistance: $distance")
        val current = _settings.value
        val newSettings = current.copy(
            focusMode = FocusMode.MANUAL,
            focusDistance = distance
        )
        _settings.value = newSettings
        camera2Engine.applySettings(newSettings)
    }

    /**
     * 更新白平衡模式
     *
     * @param mode 白平衡模式，参见 [WhiteBalanceMode]
     */
    fun updateWhiteBalance(mode: WhiteBalanceMode) {
        Logger.d(Logger.Tag.CAMERA, "updateWhiteBalance: $mode")
        val current = _settings.value
        val newSettings = current.copy(whiteBalanceMode = mode)
        _settings.value = newSettings
        camera2Engine.applySettings(newSettings)
    }

    /**
     * 更新缩放比例
     *
     * @param ratio 缩放比例，1.0 表示未缩放，最大值由 [CameraCapability.maxZoomRatio] 决定
     */
    fun updateZoomRatio(ratio: Float) {
        Logger.d(Logger.Tag.CAMERA, "updateZoomRatio: $ratio")
        val current = _settings.value
        val newSettings = current.copy(zoomRatio = ratio)
        _settings.value = newSettings
        camera2Engine.applySettings(newSettings)
    }

    /**
     * 更新闪光灯模式
     *
     * @param mode 闪光灯模式，参见 [FlashMode]
     */
    fun updateFlashMode(mode: FlashMode) {
        Logger.d(Logger.Tag.CAMERA, "updateFlashMode: $mode")
        val current = _settings.value
        val newSettings = current.copy(flashMode = mode)
        _settings.value = newSettings
        camera2Engine.applySettings(newSettings)
    }

    /**
     * 重置为全自动模式
     *
     * 将所有参数恢复为 [CameraSettings.DEFAULT]。
     * 适用于用户一键切回自动模式。
     */
    fun resetToAuto() {
        Logger.d(Logger.Tag.CAMERA, "resetToAuto")
        _settings.value = CameraSettings.DEFAULT
        camera2Engine.applySettings(CameraSettings.DEFAULT)
    }

    // ── 拍照 ──

    /**
     * 拍照
     *
     * 委托给 [Camera2Engine.captureStill] 执行拍照。
     * Controller 只负责流程协调，不直接操作 Camera2 API。
     *
     * 拍照流程：
     * 1. 检查相机状态（必须处于 Previewing 状态）
     * 2. 调用 Camera2Engine.captureStill() 执行拍照
     * 3. 拍照完成后通过回调返回 [PhotoResult]
     *
     * 设计原则：
     * - Controller 仅协调流程，不直接操作 CameraDevice
     * - 不重建 CameraSession
     * - 拍照后自动恢复预览
     *
     * @param onResult 拍照结果回调
     */
    fun capturePhoto(onResult: (PhotoResult) -> Unit) {
        if (!isPreviewActive) {
            Logger.w(Logger.Tag.CAMERA, "Cannot capture photo: preview not active")
            onResult(PhotoResult.Error("Preview not active"))
            return
        }

        Logger.d(Logger.Tag.CAMERA, "capturePhoto called")
        camera2Engine.captureStill(onResult)
    }

    // ════════════════════════════════════════════════════════════════
    // 视频录制
    // ════════════════════════════════════════════════════════════════

    /**
     * 开始录制视频
     *
     * Controller 仅负责流程协调：
     * 1. 检查预览是否激活
     * 2. 通过 [VideoFileManager] 创建输出文件
     * 3. 委托 [Camera2Engine.startRecording] 启动录制
     *
     * @param outputPath 输出文件路径
     * @param onResult 录制启动结果回调（true=成功，false=失败）
     */
    fun startRecording(
        outputPath: String,
        onResult: (Boolean) -> Unit
    ) {
        if (!isPreviewActive) {
            Logger.w(Logger.Tag.CAMERA, "Cannot start recording: preview not active")
            onResult(false)
            return
        }

        if (camera2Engine.isRecording()) {
            Logger.w(Logger.Tag.CAMERA, "Already recording")
            onResult(false)
            return
        }

        Logger.d(Logger.Tag.CAMERA, "startRecording called: $outputPath")
        camera2Engine.startRecording(outputPath, onResult)
    }

    /**
     * 停止录制视频
     *
     * Controller 仅负责流程协调：
     * 1. 委托 [Camera2Engine.stopRecording] 停止录制
     * 2. 录制完成后通过 [VideoFileManager] 保存到 MediaStore
     *
     * @param onComplete 录制完成回调（参数为输出文件路径）
     */
    fun stopRecording(onComplete: ((String) -> Unit)? = null) {
        if (!camera2Engine.isRecording()) {
            Logger.w(Logger.Tag.CAMERA, "Not recording")
            return
        }

        Logger.d(Logger.Tag.CAMERA, "stopRecording called")

        val outputPath = camera2Engine.getVideoRecorderManager().getOutputPath()
        camera2Engine.stopRecording()
        onComplete?.invoke(outputPath)
    }

    /**
     * 是否正在录制
     */
    fun isRecording(): Boolean = camera2Engine.isRecording()

    /**
     * 获取录制器状态
     */
    fun getRecorderState(): RecorderState = camera2Engine.getVideoRecorderManager().state

    // ── 查询方法 ──

    /**
     * 获取可用相机列表
     *
     * @return 所有可用相机的信息列表
     */
    fun getAvailableCameras(): List<CameraInfo> {
        return try {
            capabilityDetector.getAllCameraIds().map { cameraId ->
                val caps = capabilityDetector.getCapabilities(cameraId)
                CameraInfo(
                    id = cameraId,
                    facing = caps?.facing ?: 0,
                    isLogical = caps?.isLogicalCamera ?: false,
                    capabilities = caps
                )
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAMERA, e, "Failed to get available cameras")
            emptyList()
        }
    }

    /**
     * 获取支持的视频分辨率列表
     *
     * @return 当前相机支持的视频分辨率列表
     */
    fun getSupportedVideoSizes(): List<ResolutionConfig> {
        val caps = currentCapabilities ?: return ResolutionConfig.STANDARD_VIDEO_RESOLUTIONS
        val supportedSizes = caps.supportedVideoSizes
        if (supportedSizes.isEmpty()) return ResolutionConfig.STANDARD_VIDEO_RESOLUTIONS

        return ResolutionConfig.STANDARD_VIDEO_RESOLUTIONS.filter { config ->
            supportedSizes.any { it.width == config.width && it.height == config.height }
        }.ifEmpty { ResolutionConfig.STANDARD_VIDEO_RESOLUTIONS }
    }

    /**
     * 获取支持的 FPS 列表
     *
     * @return 当前相机支持的 FPS 值列表
     */
    fun getSupportedFpsValues(): List<FpsConfig> {
        val caps = currentCapabilities ?: return FpsConfig.STANDARD_FPS_VALUES
        val maxFps = caps.maxVideoFps

        return FpsConfig.STANDARD_FPS_VALUES.filter { it.fps <= maxFps }
            .ifEmpty { listOf(FpsConfig.DEFAULT) }
    }
}

/**
 * 相机状态
 *
 * 状态流转：
 * ```
 * Idle → Initialized → Opening → Opened → Previewing → Closing → Idle
 *  ↓         ↓           ↓         ↓          ↓
 * Error ← Error ← Error ← Error ← Error
 * ```
 */
sealed class CameraState {
    /** 空闲状态，未初始化 */
    data object Idle : CameraState()

    /** 已初始化（Camera2Engine 就绪，能力已检测） */
    data object Initialized : CameraState()

    /** 正在打开相机 */
    data object Opening : CameraState()

    /** 相机已打开（CameraDevice 就绪） */
    data object Opened : CameraState()

    /** 预览已激活 */
    data object Previewing : CameraState()

    /** 正在切换相机 */
    data object SwitchingCamera : CameraState()

    /** 正在关闭 */
    data object Closing : CameraState()

    /** 错误 */
    data class Error(val message: String) : CameraState()
}

/**
 * 相机信息
 *
 * @property id 相机 ID
 * @property facing 相机朝向（CameraCharacteristics.LENS_FACING_*）
 * @property isLogical 是否为逻辑相机
 * @property capabilities 相机能力信息
 */
data class CameraInfo(
    val id: String,
    val facing: Int,
    val isLogical: Boolean,
    val capabilities: CameraCapabilities?
)
