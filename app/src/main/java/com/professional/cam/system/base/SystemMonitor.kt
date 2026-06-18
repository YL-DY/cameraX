package com.professional.cam.system.base

import kotlinx.coroutines.flow.StateFlow

/**
 * 系统监控器接口
 *
 * 所有系统监控器的统一接口。
 * 每个监控器负责一个系统指标（CPU、内存、电池、温度、存储、录制时间）。
 *
 * 设计原则：
 * - 每个监控器独立运行
 * - 通过 StateFlow 暴露当前值
 * - 支持启动/停止监控
 * - 支持告警阈值
 */
interface SystemMonitor<T> {

    /** 监控器名称 */
    val name: String

    /** 当前值 */
    val currentValue: StateFlow<T>

    /** 是否正在监控 */
    val isActive: StateFlow<Boolean>

    /** 启动监控 */
    fun start()

    /** 停止监控 */
    fun stop()

    /** 获取当前值的可读字符串 */
    fun getFormattedValue(): String
}
