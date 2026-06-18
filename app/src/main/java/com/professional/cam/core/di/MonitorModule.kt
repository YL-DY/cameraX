package com.professional.cam.core.di

import com.professional.cam.system.cpu.CpuMonitor
import com.professional.cam.system.memory.MemoryMonitor
import com.professional.cam.system.battery.BatteryMonitor
import com.professional.cam.system.temperature.TemperatureMonitor
import com.professional.cam.system.storage.StorageMonitor
import com.professional.cam.system.timer.RecordingTimer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

/**
 * 系统监控模块依赖注入
 *
 * 提供 Activity 级别的监控依赖：
 * - [CpuMonitor]: CPU 监控
 * - [MemoryMonitor]: 内存监控
 * - [BatteryMonitor]: 电池监控
 * - [TemperatureMonitor]: 温度监控
 * - [StorageMonitor]: 存储监控
 * - [RecordingTimer]: 录像计时器
 */
@Module
@InstallIn(ActivityComponent::class)
object MonitorModule {

    @Provides
    @ActivityScoped
    fun provideCpuMonitor(): CpuMonitor {
        return CpuMonitor()
    }

    @Provides
    @ActivityScoped
    fun provideMemoryMonitor(): MemoryMonitor {
        return MemoryMonitor()
    }

    @Provides
    @ActivityScoped
    fun provideBatteryMonitor(): BatteryMonitor {
        return BatteryMonitor()
    }

    @Provides
    @ActivityScoped
    fun provideTemperatureMonitor(): TemperatureMonitor {
        return TemperatureMonitor()
    }

    @Provides
    @ActivityScoped
    fun provideStorageMonitor(): StorageMonitor {
        return StorageMonitor()
    }

    @Provides
    @ActivityScoped
    fun provideRecordingTimer(): RecordingTimer {
        return RecordingTimer()
    }
}
