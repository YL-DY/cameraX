package com.professional.cam.camera.manager

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.professional.cam.camera.capability.CapabilityDetector
import com.professional.cam.camera.config.CameraConfig
import com.professional.cam.core.error.AppError
import com.professional.cam.core.error.ErrorHandler
import com.professional.cam.core.error.ErrorRecoveryManager
import com.professional.cam.core.error.RecoveryAction
import com.professional.cam.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera2 专业相机引擎
 *
 * 职责（仅限 Step 1 Camera Foundation）：
 * - 打开/关闭 [CameraDevice]
 * - 创建/管理 [CameraCaptureSession]
 * - 启动/停止预览 [CaptureRequest]
 * - 管理专用 [HandlerThread] 确保所有 Camera2 操作在独立线程执行
 * - 完整的错误处理和自动恢复机制
 * - 所有资源正确释放，无泄漏
 *
 * 注意：UI 层不允许直接操作此类，必须通过 [CameraController]。
 */
@Singleton
class Camera2Engine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityDetector: CapabilityDetector,
    private val errorHandler: ErrorHandler,
    private val errorRecoveryManager: ErrorRecoveryManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Camera2 核心对象 ──
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    // ── 线程管理 ──
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null

    // ── Surface ──
    private var previewSurface: Surface? = null

    // ── 预览请求 ──
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    // ── 状态 ──
    private val _state = MutableStateFlow<CameraEngineState>(CameraEngineState.Closed)
    val state: StateFlow<CameraEngineState> = _state.asStateFlow()

    // ── 当前配置 ──
    private var currentConfig: CameraConfig? = null

    // ── 捕获结果回调 ──
    private var onCaptureResultCallback: ((CaptureResult) -> Unit)? = null

    // ── 是否已初始化 ──
    private var isInitialized = false

    /**
     * 初始化 CameraManager 和 HandlerThread
     *
     * 必须在任何相机操作之前调用。
     * 多次调用安全，不会重复初始化。
     */
    fun initialize() {
        if (isInitialized) {
            Logger.d(Logger.Tag.CAMERA, "Camera2Engine already initialized")
            return
        }

        Logger.d(Logger.Tag.CAMERA, "Camera2Engine initializing...")
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startCameraThread()
        isInitialized = true
        Logger.d(Logger.Tag.CAMERA, "Camera2Engine initialized successfully")
    }

    /**
     * 打开相机设备
     *
     * @param config 相机配置，包含 cameraId
     * @param onStateChanged 状态变化回调（可选），用于接收实时状态更新
     */
    fun openCamera(
        config: CameraConfig,
        onStateChanged: ((CameraEngineState) -> Unit)? = null
    ) {
        val manager = cameraManager
        if (manager == null) {
            Logger.e(Logger.Tag.CAMERA, "CameraManager not initialized, call initialize() first")
            updateState(CameraEngineState.Error("CameraManager not initialized"), onStateChanged)
            return
        }

        // 如果已有打开的相机，先关闭
        if (cameraDevice != null) {
            Logger.d(Logger.Tag.CAMERA, "Camera already open, closing first")
            closeInternal()
        }

        currentConfig = config
        Logger.d(Logger.Tag.CAMERA, "Opening camera: ${config.cameraId}")

        updateState(CameraEngineState.Opening, onStateChanged)

        try {
            // 先检查相机是否存在且有权限
            try {
                manager.getCameraCharacteristics(config.cameraId)
            } catch (e: SecurityException) {
                Logger.e(Logger.Tag.CAMERA, "Camera permission denied for ${config.cameraId}")
                updateState(
                    CameraEngineState.Error("Camera permission denied"),
                    onStateChanged
                )
                return
            } catch (e: IllegalArgumentException) {
                Logger.e(Logger.Tag.CAMERA, "Camera ${config.cameraId} not found")
                updateState(
                    CameraEngineState.Error("Camera ${config.cameraId} not found"),
                    onStateChanged
                )
                return
            }

            val callback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Logger.d(Logger.Tag.CAMERA, "Camera opened: ${config.cameraId}")
                    cameraDevice = camera
                    errorRecoveryManager.resetReconnectAttempts()
                    updateState(CameraEngineState.Opened, onStateChanged)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Logger.w(Logger.Tag.CAMERA, "Camera disconnected: ${config.cameraId}")
                    camera.close()
                    cameraDevice = null
                    updateState(CameraEngineState.Disconnected, onStateChanged)

                    // 触发自动重连
                    scope.launch {
                        val recovered = errorRecoveryManager.executeRecovery(
                            RecoveryAction.RECONNECT_CAMERA
                        )
                        if (recovered) {
                            Logger.d(Logger.Tag.CAMERA, "Attempting to reconnect camera")
                            currentConfig?.let { openCamera(it, onStateChanged) }
                        } else {
                            Logger.e(Logger.Tag.CAMERA, "Max reconnect attempts reached")
                            updateState(
                                CameraEngineState.Error("Camera disconnected, max reconnects reached"),
                                onStateChanged
                            )
                        }
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    val errorMsg = when (error) {
                        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> {
                            "Camera in use by another app"
                        }
                        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> {
                            "Maximum cameras in use"
                        }
                        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> {
                            "Camera disabled by device policy"
                        }
                        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> {
                            "Camera device encountered a fatal error"
                        }
                        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> {
                            "Camera service encountered a fatal error"
                        }
                        else -> "Unknown camera error: $error"
                    }
                    Logger.e(Logger.Tag.CAMERA, "Camera error [$error]: $errorMsg")
                    camera.close()
                    cameraDevice = null
                    updateState(CameraEngineState.Error(errorMsg), onStateChanged)

                    // 触发自动恢复
                    scope.launch {
                        val recoveryAction = when (error) {
                            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
                            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> {
                                RecoveryAction.WAIT_AND_RETRY
                            }
                            else -> RecoveryAction.RETRY_OPEN_CAMERA
                        }
                        val recovered = errorRecoveryManager.executeRecovery(recoveryAction)
                        if (recovered) {
                            Logger.d(Logger.Tag.CAMERA, "Attempting camera recovery after error")
                            currentConfig?.let { openCamera(it, onStateChanged) }
                        } else {
                            Logger.e(Logger.Tag.CAMERA, "Camera recovery failed after error")
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.openCamera(config.cameraId, context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                manager.openCamera(config.cameraId, callback, cameraHandler)
            }
        } catch (e: SecurityException) {
            Logger.e(Logger.Tag.CAMERA, e, "Security exception opening camera")
            updateState(CameraEngineState.Error("Camera permission denied"), onStateChanged)
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAMERA, e, "Failed to open camera")
            updateState(
                CameraEngineState.Error("Failed to open camera: ${e.message}"),
                onStateChanged
            )
        }
    }

    /**
     * 创建 Capture Session
     *
     * @param surface 预览 Surface（来自 CameraX）
     * @param onStateChanged 状态变化回调（可选）
     */
    fun createCaptureSession(
        surface: Surface,
        onStateChanged: ((CameraEngineState) -> Unit)? = null
    ) {
        val device = cameraDevice
        if (device == null) {
            Logger.w(Logger.Tag.CAMERA, "Cannot create session: camera device is null")
            updateState(
                CameraEngineState.Error("Camera device not available"),
                onStateChanged
            )
            return
        }

        this.previewSurface = surface

        Logger.d(Logger.Tag.CAMERA, "Creating capture session")

        try {
            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Logger.d(Logger.Tag.CAMERA, "Capture session configured successfully")
                    captureSession = session
                    updateState(CameraEngineState.SessionCreated, onStateChanged)

                    // 自动启动预览
                    startPreviewRequest(onStateChanged)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Logger.e(Logger.Tag.CAMERA, "Capture session configure failed")
                    captureSession = null
                    updateState(
                        CameraEngineState.Error("Session configure failed"),
                        onStateChanged
                    )

                    // 触发会话重建
                    scope.launch {
                        val recovered = errorRecoveryManager.executeRecovery(
                            RecoveryAction.RECREATE_SESSION
                        )
                        if (recovered) {
                            Logger.d(Logger.Tag.CAMERA, "Retrying session creation")
                            previewSurface?.let { createCaptureSession(it, onStateChanged) }
                        }
                    }
                }

                override fun onActive(session: CameraCaptureSession) {
                    Logger.d(Logger.Tag.CAMERA, "Capture session active")
                }

                override fun onClosed(session: CameraCaptureSession) {
                    Logger.d(Logger.Tag.CAMERA, "Capture session closed")
                    if (captureSession == session) {
                        captureSession = null
                    }
                }

                override fun onSurfacePrepared(
                    session: CameraCaptureSession,
                    surface: Surface
                ) {
                    Logger.d(Logger.Tag.CAMERA, "Surface prepared for session")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = listOf(
                    android.hardware.camera2.params.OutputConfiguration(surface)
                )
                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    context.mainExecutor,
                    stateCallback
                )
                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    listOf(surface),
                    stateCallback,
                    cameraHandler
                )
            }
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAMERA, e, "Failed to create capture session")
            updateState(
                CameraEngineState.Error("Session creation failed: ${e.message}"),
                onStateChanged
            )
        }
    }

    /**
     * 启动预览请求（重复请求）
     */
    private fun startPreviewRequest(
        onStateChanged: ((CameraEngineState) -> Unit)? = null
    ) {
        val session = captureSession
        if (session == null) {
            Logger.w(Logger.Tag.CAMERA, "Cannot start preview: session is null")
            return
        }

        val device = cameraDevice
        if (device == null) {
            Logger.w(Logger.Tag.CAMERA, "Cannot start preview: device is null")
            return
        }

        try {
            // 创建预览请求构建器
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewSurface?.let { builder.addTarget(it) }
            previewRequestBuilder = builder

            val request = builder.build()
            Logger.d(Logger.Tag.CAMERA, "Starting preview repeating request")

            session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    onCaptureResultCallback?.invoke(result)
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CameraCaptureSession.CaptureFailure
                ) {
                    Logger.w(Logger.Tag.CAMERA, "Capture failed: ${failure.reason}")
                }

                override fun onCaptureSequenceCompleted(
                    session: CameraCaptureSession,
                    sequenceId: Int,
                    frameNumber: Long
                ) {
                    // 正常捕获完成
                }

                override fun onCaptureSequenceAborted(
                    session: CameraCaptureSession,
                    sequenceId: Int
                ) {
                    Logger.w(Logger.Tag.CAMERA, "Capture sequence aborted: $sequenceId")
                }
            }, cameraHandler)

            updateState(CameraEngineState.PreviewActive, onStateChanged)
            Logger.d(Logger.Tag.CAMERA, "Preview repeating request started")
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAMERA, e, "Failed to start preview request")
            updateState(
                CameraEngineState.Error("Preview start failed: ${e.message}"),
                onStateChanged
            )
        }
    }

    /**
     * 更新预览请求参数
     *
     * 用于后续专业控制（ISO、快门、WB 等）更新 CaptureRequest 参数。
     *
     * @param params 参数更新 lambda，接收 [CaptureRequest.Builder] 进行操作
     */
    fun updatePreviewRequest(params: CaptureRequest.Builder.() -> Unit) {
        val builder = previewRequestBuilder
        if (builder == null) {
            Logger.w(Logger.Tag.CAMERA, "Cannot update preview: builder is null")
            return
        }

        val session = captureSession
        if (session == null) {
            Logger.w(Logger.Tag.CAMERA, "Cannot update preview: session is null")
            return
        }

        try {
            builder.params()
            val request = builder.build()
            session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    onCaptureResultCallback?.invoke(result)
                }
            }, cameraHandler)
            Logger.d(Logger.Tag.CAMERA, "Preview request updated")
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAMERA, e, "Failed to update preview request")
        }
    }

    /**
     * 设置捕获结果回调
     *
     * 用于获取每帧的 [CaptureResult]，后续可用于：
     * - 自动曝光/白平衡跟踪
     * - 对焦状态监控
     * - 帧率统计
     */
    fun setOnCaptureResultCallback(callback: ((CaptureResult) -> Unit)?) {
        onCaptureResultCallback = callback
    }

    /**
     * 获取当前 CameraDevice（仅内部使用，不对外暴露）
     */
    internal fun getCameraDevice(): CameraDevice? = cameraDevice

    /**
     * 获取当前 CaptureSession（仅内部使用，不对外暴露）
     */
    internal fun getCaptureSession(): CameraCaptureSession? = captureSession

    /**
     * 获取当前预览请求构建器（用于专业控制更新）
     */
    internal fun getPreviewRequestBuilder(): CaptureRequest.Builder? = previewRequestBuilder

    /**
     * 关闭相机（内部方法，不触发状态通知）
     */
    private fun closeInternal() {
        Logger.d(Logger.Tag.CAMERA, "Closing camera (internal)")

        try {
            // 1. 先中止所有捕获
            try {
                captureSession?.abortCaptures()
            } catch (e: Exception) {
                Logger.w(Logger.Tag.CAMERA, e, "Error aborting captures")
            }

            // 2. 关闭 Session
            try {
                captureSession?.close()
            } catch (e: Exception) {
                Logger.w(Logger.Tag.CAMERA, e, "Error closing session")
            }
            captureSession = null

            // 3. 关闭 CameraDevice
            try {
                cameraDevice?.close()
            } catch (e: Exception) {
                Logger.w(Logger.Tag.CAMERA, e, "Error closing camera device")
            }
            cameraDevice = null

            // 4. 清理引用
            previewSurface = null
            previewRequestBuilder = null
            onCaptureResultCallback = null
        } catch (e: Exception) {
            Logger.e(Logger.Tag.CAMERA, e, "Error during camera close")
        }
    }

    /**
     * 关闭相机
     *
     * 按正确顺序释放资源：
     * 1. abortCaptures() - 中止所有进行中的捕获
     * 2. session.close() - 关闭 CaptureSession
     * 3. device.close() - 关闭 CameraDevice
     * 4. 清理所有引用
     */
    fun close() {
        Logger.d(Logger.Tag.CAMERA, "Closing camera")
        closeInternal()
        _state.value = CameraEngineState.Closed
        Logger.d(Logger.Tag.CAMERA, "Camera closed")
    }

    // ════════════════════════════════════════════════════════════════
    // 预留接口（后续步骤实现）
    // 当前仅定义接口签名，不实现业务逻辑。
    // 这些接口确保后续扩展无需重构 Camera2Engine。
    // ════════════════════════════════════════════════════════════════

    /**
     * 更新曝光补偿
     *
     * 通过 [CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION] 设置曝光补偿值。
     *
     * @param ev 曝光补偿值（EV 单位），范围由 [CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE] 决定
     */
    fun updateExposure(ev: Int) {
        Logger.d(Logger.Tag.CAMERA, "updateExposure($ev) - reserved, not yet implemented")
        // TODO: Step 4 - Manual Controls
    }

    /**
     * 更新 ISO 感光度
     *
     * 通过 [CaptureRequest.SENSOR_SENSITIVITY] 设置 ISO 值。
     * 需要关闭自动增益控制（AE）。
     *
     * @param iso ISO 值，范围由 [CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE] 决定
     */
    fun updateIso(iso: Int) {
        Logger.d(Logger.Tag.CAMERA, "updateIso($iso) - reserved, not yet implemented")
        // TODO: Step 4 - Manual Controls
    }

    /**
     * 更新对焦距离
     *
     * 通过 [CaptureRequest.LENS_FOCUS_DISTANCE] 设置对焦距离。
     * 需要关闭自动对焦（AF）。
     *
     * @param distance 对焦距离（米），范围由 [CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE] 决定
     */
    fun updateFocus(distance: Float) {
        Logger.d(Logger.Tag.CAMERA, "updateFocus($distance) - reserved, not yet implemented")
        // TODO: Step 4 - Manual Controls
    }

    /**
     * 更新白平衡色温
     *
     * 通过 [CaptureRequest.COLOR_CORRECTION_MODE] 和
     * [CaptureRequest.COLOR_CORRECTION_GAINS] 设置白平衡。
     * 需要关闭自动白平衡（AWB）。
     *
     * @param kelvin 色温值（开尔文），范围由 [CameraCharacteristics.SENSOR_INFO_WHITE_BALANCE_RANGE] 决定
     */
    fun updateWhiteBalance(kelvin: Int) {
        Logger.d(Logger.Tag.CAMERA, "updateWhiteBalance($kelvin) - reserved, not yet implemented")
        // TODO: Step 4 - Manual Controls
    }

    /**
     * 开始录像
     *
     * 创建 [MediaRecorder] 或 [android.media.MediaCodec] 实例，
     * 配置视频/音频编码器，并提交录像 CaptureRequest。
     */
    fun startRecording() {
        Logger.d(Logger.Tag.CAMERA, "startRecording() - reserved, not yet implemented")
        // TODO: Step 5 - Video Recording
    }

    /**
     * 停止录像
     *
     * 停止录像 CaptureRequest，释放编码器资源，保存视频文件。
     */
    fun stopRecording() {
        Logger.d(Logger.Tag.CAMERA, "stopRecording() - reserved, not yet implemented")
        // TODO: Step 5 - Video Recording
    }

    /**
     * 拍照（静态图像捕获）
     *
     * 使用 [CameraDevice.TEMPLATE_STILL_CAPTURE] 创建单帧捕获请求，
     * 通过 [CameraCaptureSession.capture] 执行拍照。
     * 支持 JPEG、RAW 或 JPEG+RAW 输出。
     */
    fun captureStill() {
        Logger.d(Logger.Tag.CAMERA, "captureStill() - reserved, not yet implemented")
        // TODO: Step 5 - Video Recording / Step 6 - Photo Capture
    }

    /**
     * 释放所有资源
     *
     * 包括关闭相机和停止 HandlerThread。
     * 调用后此实例不再可用，需要重新 [initialize]。
     */
    fun release() {
        Logger.d(Logger.Tag.CAMERA, "Releasing Camera2Engine")
        close()
        stopCameraThread()
        isInitialized = false
        Logger.d(Logger.Tag.CAMERA, "Camera2Engine released")
    }

    // ── 线程管理 ──

    private fun startCameraThread() {
        if (cameraThread != null) {
            Logger.w(Logger.Tag.CAMERA, "Camera thread already started")
            return
        }
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
        Logger.d(Logger.Tag.CAMERA, "Camera thread started")
    }

    private fun stopCameraThread() {
        try {
            cameraThread?.quitSafely()
        } catch (e: Exception) {
            Logger.w(Logger.Tag.CAMERA, e, "Error stopping camera thread")
        }
        cameraThread = null
        cameraHandler = null
        Logger.d(Logger.Tag.CAMERA, "Camera thread stopped")
    }

    // ── 状态更新 ──

    private fun updateState(
        newState: CameraEngineState,
        onStateChanged: ((CameraEngineState) -> Unit)?
    ) {
        _state.value = newState
        onStateChanged?.invoke(newState)
    }
}

/**
 * Camera2 引擎状态
 */
sealed class CameraEngineState {
    /** 已关闭 */
    data object Closed : CameraEngineState()

    /** 正在打开 */
    data object Opening : CameraEngineState()

    /** 已打开（CameraDevice 就绪） */
    data object Opened : CameraEngineState()

    /** Session 已创建 */
    data object SessionCreated : CameraEngineState()

    /** 预览已激活 */
    data object PreviewActive : CameraEngineState()

    /** 已断开连接 */
    data object Disconnected : CameraEngineState()

    /** 错误 */
    data class Error(val message: String) : CameraEngineState()
}
