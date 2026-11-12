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

/**
 * 核对 [ForegroundNotificationCoordinator] 的锁与运行态不变量：
 * 服务停止或 6 小时超时撤销通知后，在途节流补发与迟到发布均不得将 ongoing 通知重新投递到通知栏。
 */
class DownloadServiceCoordinatorTest {

    private fun runningTask(done: Int = 1): Map<String, DownloadTask> = mapOf(
        "p1" to DownloadTask(
            photoId = "p1",
            albumId = "a1",
            title = "Chap 1",
            total = 10,
            index = done,
            status = DownloadStatus.RUNNING,
        ),
    )

    @Test
    fun `停服撤销通知后已排期的补发到点不会再投递通知`() = runTest {
        var now = 1_000L
        val policy = ForegroundPolicy(progressIntervalMs = 300L, clock = { now })
        val lock = Any()
        var current = runningTask(1)
        val events = mutableListOf<String>()

        val coordinator = ForegroundNotificationCoordinator(
            policy = policy,
            lock = lock,
            scope = backgroundScope,
            latestTasks = { current },
            postNotification = { events += "post" },
            cancelNotification = { events += "cancel" },
        )

        // 首次进入前台并消耗掉立即发布窗口
        policy.onTasks(current)
        policy.onServiceForeground()
        current = runningTask(2)
        val firstActions = policy.onTasks(current)
        assertEquals(listOf(ForegroundAction.Publish(current)), firstActions)
        coordinator.publish(current)

        // 窗口内再次推进触发 ScheduleFlush
        now = 1_100L
        current = runningTask(3)
        val throttled = policy.onTasks(current)
        assertEquals(listOf(ForegroundAction.ScheduleFlush(300L)), throttled)
        coordinator.scheduleFlush(300L)

        // 补发未到点前触发停服/超时
        coordinator.stopAndCancel()
        assertEquals(listOf("post", "cancel"), events)

        // 推进虚拟时钟越过补发窗口：不得再投递通知
        now = 1_500L
        advanceTimeBy(300L)
        runCurrent()
        assertEquals(listOf("post", "cancel"), events, "停服后补发任务不得将通知重新投递回通知栏")
    }

    @Test
    fun `停服后直接调用publish不会投递通知`() = runTest {
        val policy = ForegroundPolicy()
        val lock = Any()
        val tasks = runningTask(1)
        val posted = mutableListOf<String>()
        val coordinator = ForegroundNotificationCoordinator(
            policy = policy,
            lock = lock,
            scope = backgroundScope,
            latestTasks = { tasks },
            postNotification = { posted += "post" },
            cancelNotification = { posted += "cancel" },
        )

        policy.onTasks(tasks)
        policy.onServiceForeground()
        coordinator.stopAndCancel()
        coordinator.publish(tasks)

        assertEquals(listOf("cancel"), posted, "policy 非运行态时 publish 必须被忽略")
    }
}
