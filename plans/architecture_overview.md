# Professional Video Recorder - Architecture Overview

## 项目信息

| 项目 | 值 |
|------|-----|
| Package | `com.professional.cam` |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Compile SDK | 34 |
| Language | Kotlin 100% |
| Architecture | MVVM |
| UI | Jetpack Compose |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |

---

## 一、产品定位

Android 平台最稳定、最可靠的专业录像应用。

### 核心原则
**稳定 > 性能 > 功能数量**

任何新增功能必须满足以下至少一项：
1. 提高录像可靠性
2. 提高专业控制能力
3. 提高实时监看能力

### Phase 1 (MVP) 功能范围

| 模块 | 功能 |
|------|------|
| 录像核心 | CameraX Preview + Camera2 专业控制、长时间稳定录像、镜头切换、分辨率/FPS 选择、音频录制、文件安全保存、异常恢复 |
| 专业控制 | ISO、Shutter Speed、White Balance、Manual Focus、Exposure Compensation |
| 专业监看 | Histogram、Waveform、Zebra、Focus Peaking、LUT Preview |
| 系统监控 | CPU、Memory、Battery、Device Temperature、Storage、Recording Time |

### 禁止开发
- AI / HDR+ / Night Mode / 多帧融合
- 用户系统 / 云同步 / 社区 / 支付

---

## 二、整体架构分层

```
┌──────────────────────────────────────────────────────────────────┐
│                     Presentation Layer                           │
│  Jetpack Compose UI / ViewModel / Screen / Component            │
│  CameraScreen / ControlsPanel / MonitorOverlay / SysMonitor     │
│  UI 层只通过 CameraController 与相机交互                         │
├──────────────────────────────────────────────────────────────────┤
│                     Domain Layer                                 │
│  UseCase / Model / Repository Interface                         │
├──────────────────────────────────────────────────────────────────┤
│                     Data Layer                                   │
│  RepositoryImpl / DataStore / FileManager / PresetStorage       │
├──────────────────────────────────────────────────────────────────┤
│                     Camera Engine Layer                          │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   CameraController                        │   │
│  │  统一入口，UI 层唯一交互对象，封装所有相机操作              │   │
│  └──────────┬───────────────────────────────────────────────┘   │
│             │                                                    │
│    ┌────────┼────────┐                                           │
│    ▼        ▼        ▼                                           │
│ ┌──────┐ ┌──────┐ ┌──────┐                                      │
│ │Camera2│ │CameraX│ │Image │  ← 所有引擎模块化，可替换/扩展      │
│ │Engine │ │Preview│ │Engine│                                      │
│ └──────┘ └──────┘ └──────┘                                      │
├──────────────────────────────────────────────────────────────────┤
│                     Image Pipeline Layer                         │
│  与 Camera Pipeline 完全解耦，独立处理帧数据                      │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐       │
│  │Histo-  │ │Wave-   │ │Zebra   │ │Focus   │ │ LUT    │       │
│  │gram    │ │form    │ │        │ │Peaking │ │        │       │
│  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘       │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                   │
│  │ HDR    │ │ RAW    │ │ AI     │ │ Future │  ← 预留扩展接口    │
│  │(Future)│ │Process │ │(Future)│ │ ...    │                   │
│  └────────┘ └────────┘ └────────┘ └────────┘                   │
├──────────────────────────────────────────────────────────────────┤
│                     Professional Controls Layer                  │
│  ISO / Shutter / WB / Focus / Exposure / Flash / Zoom           │
│  所有控制通过 CameraController 下发到 Camera2 Engine             │
├──────────────────────────────────────────────────────────────────┤
│                     System Monitor Layer                         │
│  CPU / Memory / Battery / Temp / Storage / Timer                │
├──────────────────────────────────────────────────────────────────┤
│                     Core / Common Layer                          │
│  DI (Hilt) / Error Handling / Permission / Extension / Util     │
│  CapabilityDetector (设备能力检测，全局唯一)                      │
└──────────────────────────────────────────────────────────────────┘
```

### 架构核心原则

1. **UI 层不允许直接操作 Camera2 API** — 所有相机操作统一经过 [`CameraController`](plans/architecture_overview.md:1)
2. **Camera Engine 完全模块化** — Camera2 Engine、CameraX Preview、Image Engine 各自独立，可替换
3. **Image Pipeline 与 Camera Pipeline 解耦** — 帧数据通过独立通道传递，为未来扩展预留接口
4. **无品牌硬编码** — 所有兼容性基于 [`CameraCharacteristics`] 和 [`CapabilityDetector`](plans/architecture_overview.md:1) 自动适配
5. **所有功能自动降级** — 不支持的功能优雅隐藏，不允许 Crash

---

## 三、CameraController — 统一相机入口

`CameraController` 是整个相机系统的**唯一对外接口**。UI 层、UseCase 层只能通过它操作相机。

```kotlin
// CameraController.kt — 统一相机控制器
class CameraController @Inject constructor(
    private val camera2Engine: Camera2Engine,
    private val cameraXPreview: CameraXPreviewManager,
    private val imageEngine: ImageEngine,
    private val capabilityDetector: CapabilityDetector,
    private val errorHandler: ErrorHandler,
    private val scope: CoroutineScope
) {
    // === 生命周期 ===
    fun initialize(cameraId: String)
    fun startPreview(surfaceProvider: SurfaceProvider)
    fun stopPreview()
    fun release()
    
    // === 录像控制 ===
    fun startRecording(config: RecordingConfig): Flow<RecordingState>
    fun stopRecording()
    fun pauseRecording()
    fun resumeRecording()
    
    // === 镜头切换 ===
    fun switchCamera(cameraId: String)
    fun getAvailableCameras(): List<CameraInfo>
    
    // === 专业控制 ===
    val isoControl: CameraControl<Int>
    val shutterControl: CameraControl<Long>
    val whiteBalanceControl: CameraControl<Int>
    val focusControl: CameraControl<Float>
    val exposureControl: CameraControl<Float>
    val flashControl: CameraControl<FlashMode>
    val zoomControl: CameraControl<Float>
    
    // === 拍照 ===
    fun capturePhoto(mode: CaptureMode): Flow<CaptureResult>
    
    // === 状态 ===
    val state: StateFlow<CameraState>
    val currentCameraId: StateFlow<String>
    val capabilities: CameraCapabilities
    
    // === 帧分析注册 ===
    fun registerFrameAnalyzer(analyzer: FrameAnalyzer)
    fun unregisterFrameAnalyzer(analyzer: FrameAnalyzer)
}
```

### UI 层交互规范

```
UI Component
    │
    ▼
ViewModel
    │ 调用 CameraController 方法
    ▼
CameraController
    │ 统一调度
    ├── ▶ Camera2Engine (专业控制 + 录像)
    ├── ▶ CameraXPreviewManager (预览 + 生命周期)
    └── ▶ ImageEngine (帧分析)
```

---

## 四、Camera Engine 模块化架构

### 4.1 Camera2Engine — 专业相机引擎

负责所有专业控制、录像、拍照。完全基于 Camera2 API。

```kotlin
// Camera2Engine.kt
class Camera2Engine @Inject constructor(
    private val context: Context,
    private val capabilityDetector: CapabilityDetector,
    private val errorHandler: ErrorHandler,
    private val scope: CoroutineScope
) {
    // Camera2 核心对象
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    // 预览 Surface（由 CameraX PreviewView 提供）
    private var previewSurface: Surface? = null
    
    // 录像 Surface（由 VideoEncoder 提供）
    private var recordingSurface: Surface? = null
    
    // 拍照 ImageReader
    private var captureReader: ImageReader? = null
    
    // 帧分析 ImageReader
    private var analysisReader: ImageReader? = null
    
    // === 核心方法 ===
    fun openCamera(cameraId: String): Flow<CameraState>
    fun createCaptureSession(surfaces: List<Surface>)
    fun startRepeatingRequest(builder: CaptureRequest.Builder)
    fun stopRepeatingRequest()
    fun closeCamera()
    
    // === 专业控制 ===
    fun createPreviewRequest(controls: List<CameraControl<*>>): CaptureRequest
    fun createRecordingRequest(controls: List<CameraControl<*>>): CaptureRequest
    
    // === 内部 ===
    private val stateCallback = object : CameraDevice.StateCallback() { ... }
    private val sessionCallback = object : CameraCaptureSession.StateCallback() { ... }
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() { ... }
}
```

### 4.2 CameraXPreviewManager — 预览引擎

仅负责 Preview 和生命周期管理。

```kotlin
// CameraXPreviewManager.kt
class CameraXPreviewManager @Inject constructor(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val processCameraProvider: ProcessCameraProvider?
    private var preview: Preview? = null
    
    // 提供 Surface 给 Camera2Engine
    val previewSurface: StateFlow<Surface?>
    
    fun startPreview(surfaceProvider: SurfaceProvider)
    fun stopPreview()
    fun bindToLifecycle(cameraId: String)
    fun unbindFromLifecycle()
    
    // CameraX 不处理任何控制参数
    // 所有控制由 Camera2Engine 通过 CaptureRequest 设置
}
```

### 4.3 ImageEngine — 图像引擎

独立于相机管线的图像处理引擎。负责拍照、RAW 处理、帧分析数据的分发。

```kotlin
// ImageEngine.kt
class ImageEngine @Inject constructor(
    private val scope: CoroutineScope
) {
    // 拍照
    private var captureReader: ImageReader? = null
    
    // 帧分析
    private val analyzers = mutableListOf<FrameAnalyzer>()
    
    // 注册帧分析器（由 Image Pipeline 使用）
    fun registerAnalyzer(analyzer: FrameAnalyzer)
    fun unregisterAnalyzer(analyzer: FrameAnalyzer)
    
    // 处理捕获的图像
    fun processCapture(image: Image, mode: CaptureMode): Flow<CaptureResult>
    
    // 分发帧到所有注册的 Analyzer
    fun dispatchFrame(image: Image)
}
```

### 引擎关系图

```
CameraController
    │
    ├──▶ Camera2Engine
    │       ├── 打开 CameraDevice
    │       ├── 创建 CaptureSession
    │       │   ├── Preview Surface (来自 CameraX)
    │       │   ├── Recording Surface (来自 VideoEncoder)
    │       │   ├── Capture ImageReader (来自 ImageEngine)
    │       │   └── Analysis ImageReader (来自 ImageEngine)
    │       ├── 设置 CaptureRequest (含所有控制参数)
    │       └── 处理 CaptureCallback
    │
    ├──▶ CameraXPreviewManager
    │       ├── 绑定 Preview UseCase 到生命周期
    │       ├── 提供 PreviewView Surface
    │       └── 不设置任何控制参数
    │
    └──▶ ImageEngine
            ├── 管理 Capture ImageReader
            ├── 管理 Analysis ImageReader
            ├── 分发帧到 FrameAnalyzer 链
            └── 处理拍照结果
```

---

## 五、Capability Detection 系统

### 5.1 完整设备能力模型

```kotlin
// CameraCapabilities.kt
data class CameraCapabilities(
    // 基础信息
    val cameraId: String,
    val hardwareLevel: Int,              // INFO_3, FULL, LEVEL_3, LIMITED
    val facing: Int,                     // BACK, FRONT, EXTERNAL
    
    // RAW
    val isRawSupported: Boolean,
    val rawSize: Size?,
    
    // 手动控制
    val isManualFocusSupported: Boolean,
    val minFocusDistance: Float,         // 0 = 不支持
    val isManualIsoSupported: Boolean,
    val isoRange: Range<Int>?,
    val isManualShutterSupported: Boolean,
    val shutterSpeedRange: Range<Long>?,
    
    // 白平衡
    val whiteBalanceModes: IntArray?,    // AWB 模式列表
    val whiteBalanceTemperatureRange: Range<Int>?, // 色温范围
    
    // 曝光
    val isExposureCompensationSupported: Boolean,
    val exposureCompensationRange: Range<Int>?,
    val exposureCompensationStep: Rational?,
    
    // 闪光灯
    val flashModes: IntArray?,
    
    // 变焦
    val isZoomSupported: Boolean,
    val maxZoomRatio: Float,
    val availableFocalLengths: FloatArray?,
    
    // 多摄
    val isLogicalCamera: Boolean,
    val physicalCameraIds: List<String>?,
    val isUltraWideAvailable: Boolean,
    val isTelephotoAvailable: Boolean,
    val isMacroAvailable: Boolean,
    
    // 帧率
    val supportedFpsRanges: Array<Range<Int>>,
    val maxCaptureFps: Int,
    val maxVideoFps: Int,
    
    // 防抖
    val isOisSupported: Boolean,
    val isEisSupported: Boolean,
    val stabilizationModes: IntArray?,
    
    // 视频
    val supportedVideoSizes: List<Size>,
    val supportedVideoCodecs: List<Int>,
    
    // 传感器
    val sensorOrientation: Int,
    val sensorSize: Size?,
    val pixelArraySize: Size?,
    
    // 其他
    val supportedNoiseReductionModes: IntArray?,
    val supportedEdgeModes: IntArray?,
    val availableColorEffects: IntArray?,
    val maxRegions: Int,                 // AF/AE/AWB 区域数
)
```

### 5.2 CapabilityDetector

```kotlin
// CapabilityDetector.kt
@Singleton
class CapabilityDetector @Inject constructor(
    private val context: Context
) {
    private val cameraManager: CameraManager = 
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    // 缓存所有相机的能力
    private val cache = mutableMapOf<String, CameraCapabilities>()
    
    // 检测所有相机
    fun detectAllCameras(): List<CameraCapabilities>
    
    // 检测指定相机
    fun detectCamera(cameraId: String): CameraCapabilities
    
    // 获取缓存
    fun getCapabilities(cameraId: String): CameraCapabilities?
    
    // 获取所有相机 ID（含逻辑相机）
    fun getAllCameraIds(): List<String>
    
    // 获取后置相机列表
    fun getBackCameraIds(): List<String>
    
    // 获取前置相机列表
    fun getFrontCameraIds(): List<String>
    
    // 判断是否支持特定功能
    fun isFeatureSupported(cameraId: String, feature: CameraFeature): Boolean
}

enum class CameraFeature {
    RAW, MANUAL_FOCUS, MANUAL_ISO, MANUAL_SHUTTER,
    WHITE_BALANCE, FLASH, ZOOM, ULTRA_WIDE, TELEPHOTO,
    MACRO, HIGH_FRAME_RATE, OIS, EIS, LOG_VIDEO,
    EXPOSURE_COMPENSATION, NOISE_REDUCTION
}
```

### 5.3 降级策略

| 功能 | 检测条件 | 不支持时 |
|------|---------|---------|
| RAW | `isRawSupported` | 隐藏 RAW 选项，仅 JPEG |
| Manual Focus | `isManualFocusSupported` | 隐藏对焦滑块，锁定 AUTO |
| Manual ISO | `isManualIsoSupported` | 隐藏 ISO 控制，锁定 AUTO |
| Manual Shutter | `isManualShutterSupported` | 隐藏快门控制，锁定 AUTO |
| White Balance | `whiteBalanceModes` 含多个模式 | 隐藏 WB 选择器，锁定 AUTO |
| Flash | `flashModes` 非空 | 隐藏闪光灯按钮 |
| Zoom | `isZoomSupported` | 隐藏变焦滑块 |
| UltraWide | `isUltraWideAvailable` | 隐藏超广角切换 |
| Telephoto | `isTelephotoAvailable` | 隐藏长焦切换 |
| High FPS | `maxVideoFps` | 限制 FPS 选择范围 |
| OIS | `isOisSupported` | 使用 EIS 或关闭 |
| Exposure Comp | `isExposureCompensationSupported` | 隐藏曝光补偿 |

### 5.4 无品牌硬编码原则

```kotlin
// ❌ 禁止
if (Build.MANUFACTURER == "Xiaomi") { ... }
if (Build.BRAND == "samsung") { ... }

// ✅ 正确
val caps = capabilityDetector.getCapabilities(cameraId)
if (caps.isManualIsoSupported) {
    isoControl.setValue(value)
}
```

所有兼容性判断必须基于 [`CameraCharacteristics`] 和 [`CameraCapabilities`]，不允许出现任何品牌/型号的硬编码。

---

## 六、Image Pipeline — 与 Camera Pipeline 解耦

### 6.1 架构

```
Camera2Engine
    │
    ├── ImageReader (YUV_420_888) ← 帧数据
    │       │
    │       ▼
    │   ImageEngine.dispatchFrame(image)
    │       │
    │       ▼
    │   ┌─────────────────────────────────────────┐
    │   │         FrameDistributor                 │
    │   │  在独立协程中并行分发到所有 Analyzer     │
    │   └──┬──────┬──────┬──────┬──────┬─────────┘
    │      │      │      │      │      │
    │      ▼      ▼      ▼      ▼      ▼
    │   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐
    │   │Hist│ │Wave│ │Zebra│ │Focus│ │ LUT│  ← Phase 1
    │   │    │ │    │ │     │ │Peak │ │    │
    │   └────┘ └────┘ └────┘ └────┘ └────┘
    │   ┌────┐ ┌────┐ ┌────┐ ┌────┐
    │   │ HDR│ │ RAW│ │ AI │ │... │  ← 预留扩展
    │   │(Fut)│ │Proc│ │(Fut)│ │    │
    │   └────┘ └────┘ └────┘ └────┘
    │
    └── ImageReader (JPEG/RAW) ← 拍照
            │
            ▼
        ImageEngine.processCapture(image, mode)
```

### 6.2 FrameAnalyzer 接口

```kotlin
// FrameAnalyzer.kt — 所有图像分析器的统一接口
interface FrameAnalyzer {
    /** 分析器名称 */
    val name: String
    
    /** 是否需要处理此帧（采样控制） */
    fun shouldProcess(frame: Frame): Boolean
    
    /** 处理帧数据 */
    suspend fun analyze(frame: Frame): AnalysisResult
    
    /** 处理结果回调 */
    val result: StateFlow<AnalysisResult?>
}

// Frame.kt — 帧数据封装
data class Frame(
    val image: Image,
    val timestamp: Long,
    val format: Int,
    val size: Size,
    val rotation: Int
)

// AnalysisResult.kt — 分析结果基类
sealed class AnalysisResult {
    data class HistogramData(val rgb: Array<IntArray>) : AnalysisResult()
    data class WaveformData(val luminance: FloatArray) : AnalysisResult()
    data class ZebraData(val overexposedPixels: List<PointF>) : AnalysisResult()
    data class PeakingData(val edgePixels: List<PointF>) : AnalysisResult()
    data class LutData(val processedBitmap: Bitmap) : AnalysisResult()
    
    // 预留扩展
    data class HdrData(val ...) : AnalysisResult()
    data class RawData(val ...) : AnalysisResult()
    data class AiData(val ...) : AnalysisResult()
}
```

### 6.3 预留扩展点

```kotlin
// 未来可以这样添加新的分析器：
class HdrFrameAnalyzer : FrameAnalyzer {
    override val name = "HDR"
    override fun shouldProcess(frame: Frame) = true
    override suspend fun analyze(frame: Frame): AnalysisResult {
        // HDR 处理逻辑
    }
    override val result = MutableStateFlow<AnalysisResult?>(null)
}

// 注册到 ImageEngine
imageEngine.registerAnalyzer(hdrFrameAnalyzer)
```

---

## 七、专业控制模块

### 7.1 CameraControl 接口

```kotlin
interface CameraControl<T> {
    /** 当前值 */
    val currentValue: StateFlow<T>
    
    /** 是否支持（基于 CapabilityDetector） */
    val isSupported: Boolean
    
    /** 值范围 */
    val range: ValueRange<T>
    
    /** 标签 */
    val label: String
    
    /** 设置值 */
    fun setValue(value: T): Result<Unit>
    
    /** 重置为自动 */
    fun resetToAuto(): Result<Unit>
    
    /** 应用到 CaptureRequest.Builder */
    fun applyToBuilder(builder: CaptureRequest.Builder)
    
    /** 从 CaptureResult 读取当前值 */
    fun readFromResult(result: CaptureResult)
}
```

### 7.2 控制实现列表

| 控制 | 类型 | 范围来源 | Camera2 Key |
|------|------|---------|-------------|
| ISO | Int | `SENSOR_INFO_SENSITIVITY_RANGE` | `CONTROL_AE_MODE` → OFF, `SENSOR_SENSITIVITY` |
| Shutter Speed | Long (ns) | `SENSOR_INFO_EXPOSURE_TIME_RANGE` | `CONTROL_AE_MODE` → OFF, `SENSOR_EXPOSURE_TIME` |
| White Balance | Int (K) | `CONTROL_AWB_AVAILABLE_MODES` + 色温范围 | `CONTROL_AWB_MODE` → OFF, `CONTROL_AWB_LOCK`, `COLOR_CORRECTION_MODE`, `COLOR_CORRECTION_GAINS` |
| Manual Focus | Float | `LENS_INFO_MINIMUM_FOCUS_DISTANCE` | `CONTROL_AF_MODE` → OFF, `LENS_FOCUS_DISTANCE` |
| Exposure Comp | Float | `CONTROL_AE_COMPENSATION_RANGE` + `STEP` | `CONTROL_AE_EXPOSURE_COMPENSATION` |
| Flash | FlashMode | `FLASH_INFO_AVAILABLE` | `FLASH_MODE` |
| Zoom | Float | `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM` | `SCALER_CROP_REGION` |

### 7.3 控制创建工厂

```kotlin
// ControlFactory.kt
class ControlFactory @Inject constructor() {
    fun createControls(capabilities: CameraCapabilities): List<CameraControl<*>> {
        return listOfNotNull(
            if (capabilities.isManualIsoSupported) IsoController(capabilities.isoRange) else null,
            if (capabilities.isManualShutterSupported) ShutterController(capabilities.shutterSpeedRange) else null,
            if (capabilities.whiteBalanceModes != null) WhiteBalanceController(capabilities.whiteBalanceTemperatureRange) else null,
            if (capabilities.isManualFocusSupported) ManualFocusController(capabilities.minFocusDistance) else null,
            if (capabilities.isExposureCompensationSupported) ExposureController(capabilities.exposureCompensationRange, capabilities.exposureCompensationStep) else null,
            if (capabilities.flashModes != null) FlashController(capabilities.flashModes) else null,
            if (capabilities.isZoomSupported) ZoomController(capabilities.maxZoomRatio) else null
        )
    }
}
```

---

## 八、录像核心

### 8.1 录像流程

```
CameraController.startRecording(config)
    │
    ▼
1. CapabilityDetector 验证配置是否支持
    │ 不支持 → 自动降级配置
    ▼
2. VideoFileManager.createOutputFile()
    │ SAF / MediaStore
    ▼
3. VideoEncoder.configure(config)
    │ MediaCodec 配置
    ▼
4. AudioRecorder.start(config)
    │ AudioRecord 启动
    ▼
5. Camera2Engine.createRecordingRequest()
    │ 创建含录像 Surface 的 CaptureRequest
    ▼
6. 开始循环
    ├── 帧写入 VideoEncoder Surface
    ├── 音频写入 AudioEncoder
    ├── MediaMuxer 混合输出
    └── 监控循环（温度/存储/时长）
```

### 8.2 分段录像策略

```kotlin
data class RecordingConfig(
    val maxDurationMs: Long = 30 * 60 * 1000L,  // 30 分钟分段
    val maxFileSizeBytes: Long = 4 * 1024 * 1024 * 1024L,  // 4GB
    val enableAutoSegment: Boolean = true
)
```

---

## 九、系统监控模块

### 9.1 SystemMonitor 接口

```kotlin
interface SystemMonitor<T : Any> {
    val currentValue: StateFlow<T>
    val name: String
    val warningThreshold: T?
    val criticalThreshold: T?
    fun start()
    fun stop()
    val intervalMs: Long
}
```

### 9.2 监控项

| 监控项 | 类型 | 间隔 | 数据来源 |
|--------|------|------|---------|
| CPU | Float (0-100) | 2s | `/proc/stat` |
| Memory | MemoryInfo | 5s | `ActivityManager` |
| Battery | BatteryInfo | 10s | `BatteryManager` |
| Temperature | Float (°C) | 10s | `PowerProfile` / 电池 |
| Storage | StorageInfo | 30s | `StatFs` |
| Recording Time | Long (ms) | 实时 | `System.currentTimeMillis()` |

---

## 十、错误处理架构

### 10.1 错误层级

```kotlin
sealed class AppError : Throwable() {
    sealed class CameraError : AppError() {
        data class OpenFailed(val cameraId: String, val reason: String) : CameraError()
        data class Disconnected(val cameraId: String) : CameraError()
        data class SessionFailed(val reason: String) : CameraError()
        data class CapabilityUnsupported(val feature: String) : CameraError()
        object AccessDenied : CameraError()
        object CameraInUse : CameraError()
    }
    
    sealed class RecordingError : AppError() {
        object InsufficientStorage : RecordingError()
        data class FileWriteFailed(val path: String) : RecordingError()
        object AudioInitFailed : RecordingError()
        data class EncodingFailed(val reason: String) : RecordingError()
        object MaxDurationReached : RecordingError()
        object MaxFileSizeReached : RecordingError()
    }
    
    sealed class SystemError : AppError() {
        data class Overheating(val temperature: Float) : SystemError()
        object LowBattery : SystemError()
        object CpuThrottling : SystemError()
        data class StorageLow(val availableBytes: Long) : SystemError()
    }
}
```

### 10.2 恢复策略

| 错误 | 恢复动作 |
|------|---------|
| CameraDisconnected | 自动重连，最多 3 次 |
| SessionAborted | 重建 CaptureSession |
| Overheating | 降级 FPS → 降级分辨率 → 停止 |
| LowBattery | 保存当前录像 |
| StorageLow | 自动停止录像 |
| FileWriteFailed | 切换到备用目录 |

---

## 十一、依赖注入 (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideCapabilityDetector(context: Context): CapabilityDetector
    
    @Provides @Singleton
    fun provideErrorHandler(): ErrorHandler
    
    @Provides @Singleton
    fun providePermissionManager(context: Context): PermissionManager
}

@Module
@InstallIn(ActivityComponent::class)
object CameraModule {
    @Provides
    fun provideCameraController(
        camera2Engine: Camera2Engine,
        cameraXPreview: CameraXPreviewManager,
        imageEngine: ImageEngine,
        capabilityDetector: CapabilityDetector,
        errorHandler: ErrorHandler,
        @ActivityScope scope: CoroutineScope
    ): CameraController
    
    @Provides
    fun provideCamera2Engine(...): Camera2Engine
    
    @Provides
    fun provideCameraXPreviewManager(...): CameraXPreviewManager
    
    @Provides
    fun provideImageEngine(): ImageEngine
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun provideSettingsDataStore(context: Context): SettingsDataStore
    
    @Provides @Singleton
    fun provideVideoFileManager(context: Context): VideoFileManager
}
```

---

## 十二、关键技术选型

| 技术 | 选型 | 理由 |
|------|------|------|
| UI | Jetpack Compose + Material3 | 现代声明式 UI |
| 架构 | MVVM + Repository | Google 官方推荐 |
| DI | Hilt | 编译期安全，Jetpack 集成 |
| 异步 | Coroutines + Flow | 轻量级，生命周期感知 |
| 相机预览 | CameraX PreviewView | 最佳 Surface 管理，仅预览 |
| 相机控制 | Camera2 | 完全控制 CaptureRequest |
| 视频编码 | MediaCodec | 硬件加速，低延迟 |
| 音频录制 | AudioRecord | 低延迟 PCM 采集 |
| 文件存储 | SAF + MediaStore | Android 10+ 推荐 |
| 设置存储 | DataStore Preferences | 异步，类型安全 |
| 日志 | Timber | 轻量级，可扩展 |
| 图像处理 | RenderScript / AGSL | GPU 加速 |
| 内存检测 | LeakCanary (debug) | 内存泄漏检测 |

---

## 十三、Phase 1 实施路线图

### Step 1: 项目初始化
- 创建 Android Studio 项目 (`com.professional.cam`)
- 配置 Gradle (Kotlin DSL)
- 配置所有依赖
- 配置 AndroidManifest (权限 + Service)
- 创建 `ProCamApplication`

### Step 2: Core 基础设施
- DI 模块 (Hilt)
- 错误处理框架
- 权限管理
- 工具类/扩展函数

### Step 3: Capability Detection
- `CameraCapabilities` 数据模型
- `CapabilityDetector` 实现
- 所有相机能力检测
- 能力缓存

### Step 4: Camera Engine
- `CameraController` 接口定义
- `Camera2Engine` 实现
- `CameraXPreviewManager` 实现
- `ImageEngine` 实现
- 预览 Surface 共享
- 镜头切换

### Step 5: 录像核心
- `VideoEncoder` + MediaCodec
- `AudioRecorder`
- `RecordingSession`
- 文件安全保存
- 异常恢复

### Step 6: 专业控制
- `CameraControl<T>` 接口
- `ControlFactory`
- ISO / Shutter / WB / Focus / Exposure / Flash / Zoom
- CaptureRequest 参数绑定

### Step 7: Image Pipeline + 专业监看
- `FrameAnalyzer` 接口
- `FrameDistributor`
- Histogram / Waveform / Zebra / Focus Peaking / LUT

### Step 8: 系统监控
- `SystemMonitor` 接口
- CPU / Memory / Battery / Temp / Storage / Timer

### Step 9: UI 整合
- 主界面 (`CameraScreen`)
- 控制面板 (`ControlsPanel`)
- 监看覆盖层 (`MonitorOverlay`)
- 系统监控面板 (`SystemMonitorPanel`)
- 设置界面 (`SettingsScreen`)

### Step 10: 测试与优化
- 长时间稳定性测试
- 多设备兼容性测试
- 性能 Profiling
- 内存泄漏检测
