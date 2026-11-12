@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadTask
import com.jm3.core.download.ForegroundAction
import com.jm3.core.download.ForegroundPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** [ForegroundRunner] 的退避补评估：时钟与调度全部走虚拟时间，不睡真实线程。 */
class ForegroundRunnerTest {

    private val retryMs = 5_000L

    private fun task(id: String): DownloadTask =
        DownloadTask(photoId = id, albumId = "album", title = "title", total = 10, status = DownloadStatus.RUNNING)

    @Test
    fun `队列静止时退避窗口结束补一次拉起`() = runTest {
        val policy = ForegroundPolicy(startRetryIntervalMs = retryMs, clock = { testScheduler.currentTime })
        val tasks = mapOf("a" to task("a"))
        var startAttempts = 0
        val runner = ForegroundRunner(
            policy = policy,
            scope = backgroundScope,
            retryIntervalMs = retryMs,
            latestTasks = { tasks },
            perform = { action ->
                if (action != ForegroundAction.Start) {
                    true
                } else {
                    startAttempts += 1
                    policy.onStartFailed()
                    false
                }
            },
        )
        // 让时钟离开 0：策略把 lastStartAttemptAt == 0 当「从未尝试」，否则退避窗口测不出来。
        advanceTimeBy(1_000)
        runner.onTasks(tasks)
        assertEquals(1, startAttempts)

        // 队列静止：没有新的队列更新，只有到点补评估能把它再拉一次。
        advanceTimeBy(retryMs - 1)
        runCurrent()
        assertEquals(1, startAttempts)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, startAttempts)
    }

    @Test
    fun `退避窗口内的新快照不重复拉起也不取消排期`() = runTest {
        val policy = ForegroundPolicy(startRetryIntervalMs = retryMs, clock = { testScheduler.currentTime })
        val tasks = mapOf("a" to task("a"))
        var startAttempts = 0
        val runner = ForegroundRunner(
            policy = policy,
            scope = backgroundScope,
            retryIntervalMs = retryMs,
            latestTasks = { tasks },
            perform = { action ->
                if (action != ForegroundAction.Start) {
                    true
                } else {
                    startAttempts += 1
                    policy.onStartFailed()
                    false
                }
            },
        )
        advanceTimeBy(1_000)
        runner.onTasks(tasks)
        assertEquals(1, startAttempts)

        // 窗口内的新快照被策略自己压住，不能因此丢掉已经排好的补评估。
        advanceTimeBy(1_000)
        runner.onTasks(tasks)
        assertEquals(1, startAttempts)

        advanceTimeBy(retryMs - 1_000 - 1)
        runCurrent()
        assertEquals(1, startAttempts)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, startAttempts)
    }

    @Test
    fun `到点前的新快照自己拉起后不再补一次`() = runTest {
        val policy = ForegroundPolicy(startRetryIntervalMs = retryMs, clock = { testScheduler.currentTime })
        val initialTasks = mapOf("a" to task("a"))
        var currentTasks = initialTasks
        var startAttempts = 0
        val runner = ForegroundRunner(
            policy = policy,
            scope = backgroundScope,
            retryIntervalMs = retryMs,
            latestTasks = { currentTasks },
            perform = { action ->
                if (action != ForegroundAction.Start) {
                    true
                } else {
                    startAttempts += 1
                    policy.onStartFailed()
                    false
                }
            },
        )
        advanceTimeBy(1_000)
        runner.onTasks(currentTasks)
        assertEquals(1, startAttempts)

        // 走到窗口末尾：补评估已到期但还没执行，此刻来了新快照消费掉唯一一次补试。
        advanceTimeBy(retryMs)
        runner.onTasks(currentTasks)
        assertEquals(2, startAttempts)

        // 到期的补评估已被这一轮撤销：同一批任务不会在当前时刻多补一次。
        runCurrent()
        assertEquals(2, startAttempts)

        // 同一批任务按 5 秒退避仅补试一次：再过一个窗口也不会无限自旋拉起。
        advanceTimeBy(retryMs)
        runCurrent()
        assertEquals(2, startAttempts)

        // 活动任务数变化开启新批次时，允许再次拉起。
        currentTasks = mapOf("a" to task("a"), "b" to task("b"))
        runner.onTasks(currentTasks)
        assertEquals(3, startAttempts)
    }

    @Test
    fun `拉起成功后不再排补评估`() = runTest {
        val policy = ForegroundPolicy(startRetryIntervalMs = retryMs, clock = { testScheduler.currentTime })
        val tasks = mapOf("a" to task("a"))
        var startAttempts = 0
        val runner = ForegroundRunner(
            policy = policy,
            scope = backgroundScope,
            retryIntervalMs = retryMs,
            latestTasks = { tasks },
            perform = { action ->
                if (action == ForegroundAction.Start) {
                    startAttempts += 1
                    policy.onServiceForeground()
                }
                true
            },
        )
        advanceTimeBy(1_000)
        runner.onTasks(tasks)
        assertEquals(1, startAttempts)

        advanceTimeBy(retryMs * 3)
        runCurrent()
        assertEquals(1, startAttempts)
    }
}
