# Professional Video Recorder - Implementation TODO List

## Phase 1 MVP 实施清单

### Step 1: 项目初始化
- [ ] 使用 Android Studio 创建新项目（Empty Compose Activity）
- [ ] 配置 `build.gradle.kts`（Project 级别）
  - [ ] 添加 Hilt 插件
  - [ ] 配置 Kotlin 版本
- [ ] 配置 `app/build.gradle.kts`
  - [ ] 添加所有依赖（CameraX、Camera2、Hilt、Compose、DataStore、Timber 等）
  - [ ] 配置 minSdk、targetSdk、compileSdk
  - [ ] 配置 ProGuard / R8 规则
- [ ] 配置 `AndroidManifest.xml`
  - [ ] 声明所有权限（CAMERA、RECORD_AUDIO、FOREGROUND_SERVICE 等）
  - [ ] 注册 Application 类
  - [ ] 声明 Foreground Service 类型（Android 13+）
- [ ] 创建 `ProCamApplication.kt`（@HiltAndroidApp）

### Step 2: Core 基础设施
- [ ] 创建 `core/di/` 模块
  - [ ] `AppModule.kt` - 提供 Application 级别依赖
  - [ ] `CameraModule.kt` - 提供相机相关依赖
  - [ ] `DataModule.kt` - 提供数据层依赖
  - [ ] `MonitorModule.kt` - 提供监控相关依赖
- [ ] 创建 `core/error/` 模块
  - [ ] `AppError.kt` - 定义 sealed class 错误层级
  - [ ] `ErrorHandler.kt` - 错误处理中心
  - [ ] `ErrorRecoveryManager.kt` - 自动恢复策略管理器
- [ ] 创建 `core/permission/` 模块
  - [ ] `PermissionManager.kt` - 运行时权限请求封装
- [ ] 创建 `core/extension/` 模块
  - [ ] `ContextExt.kt` - Context 扩展函数
  - [ ] `FileExt.kt` - 文件操作扩展
  - [ ] `BitmapExt.kt` - Bitmap 处理扩展
- [ ] 创建 `core/util/` 模块
  - [ ] `Logger.kt` - Timber 封装
  - [ ] `DateTimeUtil.kt` - 时间工具
  - [ ] `FileUtil.kt` - 文件路径工具

### Step 3: 相机核心层 - 基础架构
- [ ] 创建 `camera/config/` 模块
  - [ ] `CameraConfig.kt` - 相机配置数据类
  - [ ] `ResolutionConfig.kt` - 分辨率配置
  - [ ] `FpsConfig.kt` - FPS 配置
- [ ] 创建 `camera/manager/` 模块
  - [ ] `CameraManager.kt` - 统一相机管理器接口
  - [ ] `CameraXManager.kt` - CameraX 实现
  - [ ] `Camera2Manager.kt` - Camera2 实现
- [ ] 创建 `camera/session/` 模块
  - [ ] `SessionState.kt` - 会话状态定义
  - [ ] `SessionConfig.kt` - 会话配置
  - [ ] `RecordingSession.kt` - 录像会话管理
- [ ] 创建 `camera/pipeline/` 模块
  - [ ] `ImagePipeline.kt` - 图像处理管线
  - [ ] `FrameProcessor.kt` - 帧处理器基类
  - [ ] `SurfaceProvider.kt` - Surface 提供者

### Step 4: 录像核心完善
- [ ] 创建 `camera/audio/` 模块
  - [ ] `AudioConfig.kt` - 音频配置
  - [ ] `AudioRecorder.kt` - 音频录制器
- [ ] 实现长时间录像稳定性
  - [ ] 分段录像策略（每 30 分钟自动分段）
  - [ ] 内存监控自动降级
  - [ ] 温度监控自动降级
- [ ] 实现文件安全保存
  - [ ] `data/file/VideoFileManager.kt` - 视频文件管理器
  - [ ] `data/file/SafeFileWriter.kt` - 安全文件写入器
  - [ ] 文件完整性检查
  - [ ] 异常中断文件恢复
- [ ] 实现异常恢复
  - [ ] 相机断开自动重连
  - [ ] 录像中断自动保存
  - [ ] 会话重置机制

### Step 5: 领域层
- [ ] 创建 `domain/model/` 模块
  - [ ] `CameraSettings.kt` - 相机设置领域模型
  - [ ] `RecordingState.kt` - 录像状态模型
  - [ ] `MonitorData.kt` - 监看数据模型
  - [ ] `SystemInfo.kt` - 系统信息模型
- [ ] 创建 `domain/repository/` 模块（接口）
  - [ ] `CameraRepository.kt` - 相机仓库接口
  - [ ] `SettingsRepository.kt` - 设置仓库接口
  - [ ] `RecordingRepository.kt` - 录像仓库接口
- [ ] 创建 `domain/usecase/` 模块
  - [ ] `StartRecordingUseCase.kt`
  - [ ] `StopRecordingUseCase.kt`
  - [ ] `SwitchCameraUseCase.kt`
  - [ ] `UpdateCameraSettingsUseCase.kt`
  - [ ] `GetSystemInfoUseCase.kt`

### Step 6: 数据层
- [ ] 创建 `data/repository/` 模块
  - [ ] `SettingsRepositoryImpl.kt` - 设置仓库实现
  - [ ] `CameraConfigRepositoryImpl.kt` - 相机配置仓库实现
  - [ ] `RecordingRepositoryImpl.kt` - 录像仓库实现
- [ ] 创建 `data/local/` 模块
  - [ ] `SettingsDataStore.kt` - DataStore 封装
  - [ ] `CameraPresetStorage.kt` - 相机预设存储

### Step 7: 专业控制模块
- [ ] 创建 `control/` 模块
  - [ ] 定义统一 `CameraControl<T>` 接口
  - [ ] `IsoController.kt` - ISO 控制
  - [ ] `ShutterController.kt` - 快门速度控制
  - [ ] `WhiteBalanceController.kt` - 白平衡控制
  - [ ] `ManualFocusController.kt` - 手动对焦控制
  - [ ] `ExposureController.kt` - 曝光补偿控制
- [ ] 实现 Camera2 手动控制集成
  - [ ] 将控制参数应用到 CaptureRequest.Builder
  - [ ] 实现自动/手动模式切换

### Step 8: 专业监看模块
- [ ] 创建 `monitor/` 模块
  - [ ] 定义统一 `FrameProcessor` 接口
  - [ ] `HistogramProcessor.kt` - 直方图处理器
  - [ ] `WaveformProcessor.kt` - 波形图处理器
  - [ ] `ZebraProcessor.kt` - 斑马纹处理器
  - [ ] `FocusPeakingProcessor.kt` - 峰值对焦处理器
  - [ ] `LutProcessor.kt` - LUT 处理器
  - [ ] `LutLoader.kt` - LUT 文件加载器
- [ ] 实现帧分发机制
  - [ ] 帧采样策略（降采样以节省性能）
  - [ ] 并行处理管线

### Step 9: 系统监控模块
- [ ] 创建 `system/` 模块
  - [ ] 定义统一 `Monitor` 接口
  - [ ] `CpuMonitor.kt` - CPU 监控
  - [ ] `MemoryMonitor.kt` - 内存监控
  - [ ] `BatteryMonitor.kt` - 电池监控
  - [ ] `TemperatureMonitor.kt` - 温度监控
  - [ ] `StorageMonitor.kt` - 存储监控
  - [ ] `RecordingTimeMonitor.kt` - 录像时长监控
- [ ] 实现 `MonitorManager`
  - [ ] 统一管理所有监控项
  - [ ] 控制采集频率
  - [ ] 提供 StateFlow 输出

### Step 10: UI 层 - 基础
- [ ] 创建 `ui/theme/` 模块
  - [ ] `Theme.kt` - 应用主题
  - [ ] `Color.kt` - 颜色定义（专业暗色主题）
  - [ ] `Type.kt` - 字体定义
- [ ] 创建 `ui/MainActivity.kt`
  - [ ] 设置 Compose 内容
  - [ ] 请求必要权限
- [ ] 创建 `ui/camera/` 模块
  - [ ] `CameraScreen.kt` - 主相机界面
  - [ ] `CameraViewModel.kt` - 相机 ViewModel
  - [ ] `components/CameraPreview.kt` - 相机预览组件
  - [ ] `components/RecordingControls.kt` - 录制控制按钮
  - [ ] `components/CameraSwitcher.kt` - 镜头切换按钮

### Step 11: UI 层 - 控制面板
- [ ] 创建 `ui/controls/` 模块
  - [ ] `ControlsPanel.kt` - 控制面板容器
  - [ ] `ControlsViewModel.kt` - 控制 ViewModel
  - [ ] `components/IsoSlider.kt` - ISO 滑块
  - [ ] `components/ShutterSpeedPicker.kt` - 快门速度选择器
  - [ ] `components/WhiteBalancePicker.kt` - 白平衡选择器
  - [ ] `components/FocusSlider.kt` - 对焦滑块
  - [ ] `components/ExposureSlider.kt` - 曝光补偿滑块

### Step 12: UI 层 - 监看覆盖层
- [ ] 创建 `ui/monitor/` 模块
  - [ ] `MonitorOverlay.kt` - 监看覆盖层容器
  - [ ] `MonitorViewModel.kt` - 监看 ViewModel
  - [ ] `components/HistogramView.kt` - 直方图视图（Compose Canvas）
  - [ ] `components/WaveformView.kt` - 波形图视图（Compose Canvas）
  - [ ] `components/ZebraOverlay.kt` - 斑马纹覆盖层
  - [ ] `components/FocusPeakingOverlay.kt` - 峰值对焦覆盖层
  - [ ] `components/LutPreviewOverlay.kt` - LUT 预览覆盖层

### Step 13: UI 层 - 系统监控面板
- [ ] 创建 `ui/system/` 模块
  - [ ] `SystemMonitorPanel.kt` - 系统监控面板容器
  - [ ] `SystemMonitorViewModel.kt` - 系统监控 ViewModel
  - [ ] `components/CpuIndicator.kt` - CPU 指示器
  - [ ] `components/MemoryIndicator.kt` - 内存指示器
  - [ ] `components/BatteryIndicator.kt` - 电池指示器
  - [ ] `components/TemperatureIndicator.kt` - 温度指示器
  - [ ] `components/StorageIndicator.kt` - 存储指示器
  - [ ] `components/RecordingTimer.kt` - 录制计时器

### Step 14: UI 层 - 设置
- [ ] 创建 `ui/settings/` 模块
  - [ ] `SettingsScreen.kt` - 设置界面
  - [ ] `SettingsViewModel.kt` - 设置 ViewModel
  - [ ] 视频质量设置
  - [ ] 音频设置
  - [ ] 存储位置设置
  - [ ] 监看工具开关

### Step 15: 测试与优化
- [ ] 长时间录像稳定性测试（1 小时+）
- [ ] 镜头切换流畅性测试
- [ ] 低端设备兼容性测试
- [ ] 内存泄漏检测（LeakCanary）
- [ ] 性能 Profiling（CPU、GPU、Memory）
- [ ] 温度控制测试
- [ ] 异常场景测试（存储满、权限拒绝、相机被占用等）

---

## 依赖清单 (build.gradle.kts)

```kotlin
// Core
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.activity:activity-compose:1.8.2")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.foundation:foundation")

// CameraX
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
implementation("androidx.camera:camera-video:1.3.1")

// Camera2 (included via CameraX, but explicit for manual control)
implementation("androidx.camera:camera-camera2:1.3.1")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
kapt("com.google.dagger:hilt-android-compiler:2.50")
implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Timber (Logging)
implementation("com.jakewharton.timber:timber:5.0.1")

// LeakCanary (Debug only)
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")
```

---

## 关键设计决策记录

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| UI 框架 | XML / Compose | Compose | 现代、声明式、与 Kotlin 完美集成 |
| DI 框架 | Hilt / Koin | Hilt | 官方推荐、编译期安全、与 Jetpack 集成好 |
| 异步框架 | RxJava / Coroutines | Coroutines + Flow | 轻量级、官方推荐、与 Compose 集成好 |
| 相机 API | CameraX / Camera2 | 混合 | 兼顾易用性和专业控制 |
| 视频编码 | MediaRecorder / MediaCodec | MediaCodec | 更底层的控制、更低延迟 |
| 设置存储 | SharedPrefs / DataStore | DataStore | 异步、类型安全、无 ANR 风险 |
| 图像处理 | CPU / RenderScript / Vulkan | RenderScript + AGSL | GPU 加速、兼容性较好 |
| 文件存储 | 内部存储 / SAF / MediaStore | SAF + MediaStore | Android 10+ 推荐、用户可控 |
