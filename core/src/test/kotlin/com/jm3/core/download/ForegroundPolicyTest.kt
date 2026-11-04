package com.jm3.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForegroundPolicyTest {

    private var now = 1_000L

    private fun policy(
        progressIntervalMs: Long = 300L,
        startRetryIntervalMs: Long = 5_000L,
    ): ForegroundPolicy = ForegroundPolicy(
        startRetryIntervalMs = startRetryIntervalMs,
        progressIntervalMs = progressIntervalMs,
        clock = { now },
    )

    private fun task(id: String, status: DownloadStatus, index: Int = 0): DownloadTask =
        DownloadTask(photoId = id, albumId = "album", title = "title", total = 10, index = index, status = status)

    @Test
    fun 活动任务出现时要求拉起服务() {
        val policy = policy()

        assertEquals(listOf(ForegroundAction.Start), policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING))))
    }

    @Test
    fun 服务没进前台前同一批任务不重复拉起() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))

        now += 10_000
        assertEquals(emptyList(), policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING))))
    }

    @Test
    fun 服务进前台后按窗口发布进度() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))
        policy.onServiceForeground()

        now += 400
        val tasks = mapOf("a" to task("a", DownloadStatus.RUNNING, index = 3))
        assertEquals(listOf<ForegroundAction>(ForegroundAction.Publish(tasks)), policy.onTasks(tasks))
    }

    @Test
    fun 窗口内的连续进度改为延迟补发() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))
        policy.onServiceForeground()

        now += 400
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING, index = 3)))

        now += 100
        val tasks = mapOf("a" to task("a", DownloadStatus.RUNNING, index = 4))
        assertEquals(listOf<ForegroundAction>(ForegroundAction.ScheduleFlush(300L)), policy.onTasks(tasks))
    }

    @Test
    fun 任务到终态时绕过节流立即发布() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))
        policy.onServiceForeground()

        now += 400
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING, index = 3)))

        // 一条完成、另一条刚排进队：活动任务数仍是 1，只能靠终态跃迁绕过节流
        now += 10
        val tasks = mapOf(
            "a" to task("a", DownloadStatus.DONE, index = 10),
            "b" to task("b", DownloadStatus.QUEUED),
        )
        assertEquals(listOf<ForegroundAction>(ForegroundAction.Publish(tasks)), policy.onTasks(tasks))
    }

    @Test
    fun 活动任务清空后要求停止服务() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))
        policy.onServiceForeground()

        assertEquals(listOf(ForegroundAction.Stop), policy.onTasks(emptyMap()))
        assertTrue(policy.isRunning.not())
    }

    @Test
    fun 服务没在跑时活动任务清空不做任何事() {
        val policy = policy()

        assertEquals(emptyList(), policy.onTasks(emptyMap()))
    }

    @Test
    fun 拉起被拒后五秒内不重复拉起() {
        val policy = policy()
        assertEquals(listOf(ForegroundAction.Start), policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING))))

        // 拉起失败：服务没进前台；1 秒后活动数变化仍应被间隔压住
        now += 1_000
        assertEquals(
            emptyList(),
            policy.onTasks(
                mapOf(
                    "a" to task("a", DownloadStatus.RUNNING),
                    "b" to task("b", DownloadStatus.QUEUED),
                ),
            ),
        )

        // 距上次尝试 4.9 秒，仍不足 5 秒
        now = 5_900
        assertEquals(
            emptyList(),
            policy.onTasks(
                mapOf(
                    "a" to task("a", DownloadStatus.RUNNING),
                    "b" to task("b", DownloadStatus.QUEUED),
                    "c" to task("c", DownloadStatus.QUEUED),
                ),
            ),
        )

        // 满 5 秒后允许再试一次
        now = 6_000
        assertEquals(
            listOf(ForegroundAction.Start),
            policy.onTasks(
                mapOf(
                    "a" to task("a", DownloadStatus.RUNNING),
                    "b" to task("b", DownloadStatus.QUEUED),
                    "c" to task("c", DownloadStatus.QUEUED),
                    "d" to task("d", DownloadStatus.QUEUED),
                ),
            ),
        )
    }

    @Test
    fun 补发到期且服务仍在跑时发布进度() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))
        policy.onServiceForeground()
        val tasks = mapOf("a" to task("a", DownloadStatus.RUNNING, index = 5))

        assertEquals<ForegroundAction?>(ForegroundAction.Publish(tasks), policy.onFlushDue(tasks))

        policy.onServiceStopped()
        assertNull(policy.onFlushDue(tasks))
    }

    @Test
    fun 服务退出后只有活动数变化才重新拉起() {
        val policy = policy()
        policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING)))
        policy.onServiceForeground()
        policy.onServiceStopped()

        // 集合不变就重试等于保活：引擎规则是「启动失败不重试，只等下一次活动任务数变化」
        now += 10_000
        assertEquals(emptyList(), policy.onTasks(mapOf("a" to task("a", DownloadStatus.RUNNING))))

        assertEquals(
            listOf(ForegroundAction.Start),
            policy.onTasks(
                mapOf(
                    "a" to task("a", DownloadStatus.RUNNING),
                    "b" to task("b", DownloadStatus.QUEUED),
                ),
            ),
        )
        assertFalse(policy.isRunning)
    }
}
