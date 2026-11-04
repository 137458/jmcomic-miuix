package com.jm3.core.download

/**
 * 进度节流：[minIntervalMs] 内的重复进度丢弃，避免每页一次 notify 打爆通知服务。
 *
 * 纯逻辑、无 Android 依赖，可直接单测。
 */
class DownloadThrottle(private val minIntervalMs: Long = 300L) {

    private var lastEmitAt = NEVER

    /** [immediate] 为 true（完成/失败/活动数变化）时无条件放行并重置窗口。 */
    fun shouldEmit(nowMs: Long, immediate: Boolean = false): Boolean {
        if (immediate) {
            lastEmitAt = nowMs
            return true
        }
        if (lastEmitAt == NEVER || nowMs - lastEmitAt >= minIntervalMs) {
            lastEmitAt = nowMs
            return true
        }
        return false
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
