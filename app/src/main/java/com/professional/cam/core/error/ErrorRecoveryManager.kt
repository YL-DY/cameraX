package com.professional.cam.core.error

import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 错误恢复管理器
 *
 * 负责执行自动恢复策略：
 * - 相机打开失败自动重试（最多 3 次，间隔递增）
 * - 相机断开自动重连（最多 3 次，间隔递增）
 * - 会话异常自动重建（最多 3 次）
 * - 相机被占用等待重试（2 秒后）
 * - 过热自动降级
 * - 存储不足自动切换
 */
@Singleton
class ErrorRecoveryManager @Inject constructor() {

    private var reconnectAttempts = 0
    private var retryOpenAttempts = 0
    private var recreateSessionAttempts = 0
    private val maxAttempts = 3
    private val baseDelayMs = 500L

    /**
     * 执行恢复动作
     *
     * @return true 表示恢复成功，false 表示恢复失败（已达最大尝试次数）
     */
    suspend fun executeRecovery(action: RecoveryAction): Boolean {
        Timber.d("Executing recovery action: $action")

        return when (action) {
            RecoveryAction.RETRY_OPEN_CAMERA -> retryOpenCamera()
            RecoveryAction.RECONNECT_CAMERA -> reconnectCamera()
            RecoveryAction.RECREATE_SESSION -> recreateSession()
            RecoveryAction.SWITCH_STORAGE -> switchStorage()
            RecoveryAction.CONTINUE_WITHOUT_AUDIO -> continueWithoutAudio()
            RecoveryAction.START_NEW_SEGMENT -> startNewSegment()
            RecoveryAction.DOWNGRADE_QUALITY -> downgradeQuality()
            RecoveryAction.DOWNGRADE_FPS -> downgradeFps()
            RecoveryAction.WAIT_AND_RETRY -> waitAndRetry()
        }
    }

    /**
     * 重试打开相机
     *
     * 使用递增延迟：500ms, 1000ms, 1500ms
     */
    private suspend fun retryOpenCamera(): Boolean {
        if (retryOpenAttempts >= maxAttempts) {
            Timber.w("Max retry open camera attempts reached ($maxAttempts)")
            return false
        }

        retryOpenAttempts++
        val delayMs = baseDelayMs * retryOpenAttempts
        Timber.d("Retry open camera attempt $retryOpenAttempts/$maxAttempts (delay: ${delayMs}ms)")
        delay(delayMs)
        return true
    }

    /**
     * 重连相机
     *
     * 使用递增延迟：500ms, 1000ms, 1500ms
     */
    private suspend fun reconnectCamera(): Boolean {
        if (reconnectAttempts >= maxAttempts) {
            Timber.w("Max reconnect attempts reached ($maxAttempts)")
            return false
        }

        reconnectAttempts++
        val delayMs = baseDelayMs * reconnectAttempts
        Timber.d("Reconnect camera attempt $reconnectAttempts/$maxAttempts (delay: ${delayMs}ms)")
        delay(delayMs)
        return true
    }

    /**
     * 重建相机会话
     *
     * 使用递增延迟：300ms, 600ms, 900ms
     */
    private suspend fun recreateSession(): Boolean {
        if (recreateSessionAttempts >= maxAttempts) {
            Timber.w("Max recreate session attempts reached ($maxAttempts)")
            return false
        }

        recreateSessionAttempts++
        val delayMs = 300L * recreateSessionAttempts
        Timber.d("Recreate session attempt $recreateSessionAttempts/$maxAttempts (delay: ${delayMs}ms)")
        delay(delayMs)
        return true
    }

    /**
     * 切换存储位置
     */
    private suspend fun switchStorage(): Boolean {
        Timber.d("Switching to alternative storage path")
        delay(100)
        return true
    }

    /**
     * 无音频继续录像
     */
    private suspend fun continueWithoutAudio(): Boolean {
        Timber.d("Continuing recording without audio")
        delay(100)
        return true
    }

    /**
     * 开始新分段
     */
    private suspend fun startNewSegment(): Boolean {
        Timber.d("Starting new recording segment")
        delay(100)
        return true
    }

    /**
     * 降低录制质量
     */
    private suspend fun downgradeQuality(): Boolean {
        Timber.d("Downgrading recording quality due to overheating")
        delay(100)
        return true
    }

    /**
     * 降低帧率
     */
    private suspend fun downgradeFps(): Boolean {
        Timber.d("Downgrading recording FPS due to CPU throttling")
        delay(100)
        return true
    }

    /**
     * 等待后重试（相机被占用时使用）
     *
     * 等待 2 秒后重试
     */
    private suspend fun waitAndRetry(): Boolean {
        Timber.d("Waiting 2s before retry")
        delay(2000)
        return true
    }

    /**
     * 重置所有重试计数
     *
     * 在成功打开相机后调用。
     */
    fun resetReconnectAttempts() {
        reconnectAttempts = 0
        retryOpenAttempts = 0
        recreateSessionAttempts = 0
        Timber.d("All recovery attempt counters reset")
    }
}
