package com.jm3.app.miuix.data.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DownloadThrottle] 纯逻辑单测：进度节流窗口 + 终态立即刷新。
 *
 * 关键语义（见 DownloadNotifier.kt:181-200）：
 * - 首次调用必放行；
 * - 距上次放行小于 minIntervalMs 拒绝，恰好等于放行；
 * - 被拒的调用不推进窗口（窗口锚定最后一次真正放行的时刻）；
 * - immediate=true 无条件放行并重置窗口。
 */
class DownloadThrottleTest {

    @Test
    fun 首次调用总是放行() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(1_000L))
    }

    @Test
    fun 间隔小于阈值时拒绝重复进度() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(1_000L))
        // 299ms < 300ms 窗口：拒绝，否则每页都 notify 会打爆通知服务
        assertFalse(throttle.shouldEmit(1_299L))
    }

    @Test
    fun 间隔恰好等于阈值时放行() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(1_000L))
        assertTrue(throttle.shouldEmit(1_300L))
    }

    @Test
    fun 被拒绝的调用不推进窗口() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(1_000L))
        assertFalse(throttle.shouldEmit(1_100L))
        assertFalse(throttle.shouldEmit(1_200L))
        // 窗口仍锚定在 1000，因此 1300 才放行
        assertTrue(throttle.shouldEmit(1_300L))
    }

    @Test
    fun 完成或失败可以绕过节流立即刷新() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(1_000L))
        assertFalse(throttle.shouldEmit(1_050L))
        assertTrue(throttle.shouldEmit(1_060L, immediate = true))
    }

    @Test
    fun 立即刷新会重置节流窗口() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(1_000L))
        assertTrue(throttle.shouldEmit(1_100L, immediate = true))
        // 窗口被重置到 1100：紧随其后的普通调用应被拒绝
        assertFalse(throttle.shouldEmit(1_150L))
        assertTrue(throttle.shouldEmit(1_400L))
    }

    @Test
    fun 自定义阈值按传入值生效() {
        val throttle = DownloadThrottle(1_000L)
        assertTrue(throttle.shouldEmit(0L))
        assertFalse(throttle.shouldEmit(999L))
        assertTrue(throttle.shouldEmit(1_000L))
    }

    @Test
    fun 时钟回拨不误放行() {
        val throttle = DownloadThrottle(300L)
        assertTrue(throttle.shouldEmit(5_000L))
        // nowMs 变小（时钟回拨）：差值 < 阈值，保守拒绝
        assertFalse(throttle.shouldEmit(4_900L))
    }
}
