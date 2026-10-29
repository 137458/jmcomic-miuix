package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 队列快照上的两个纯函数单测：
 * - [activeDownloadCount]（DownloadManager.kt:161）：前台服务生命周期的唯一驱动量；
 * - [hasTerminalTransitionFrom]（DownloadManager.kt:170）：决定通知是否绕过节流立即刷新。
 */
class DownloadTaskMapsTest {

    private fun task(photoId: String, status: DownloadStatus): DownloadTask = DownloadTask(
        photoId = photoId,
        albumId = "album-$photoId",
        title = "章节 $photoId",
        total = 12,
        index = 3,
        status = status,
    )

    private fun tasks(vararg pairs: Pair<String, DownloadStatus>): Map<String, DownloadTask> =
        pairs.associate { (photoId, status) -> photoId to task(photoId, status) }

    @Test
    fun 空队列没有活动任务() {
        assertEquals(0, emptyMap<String, DownloadTask>().activeDownloadCount())
    }

    @Test
    fun 只统计排队中与运行中的任务() {
        val queue = tasks(
            "a" to DownloadStatus.QUEUED,
            "b" to DownloadStatus.RUNNING,
            "c" to DownloadStatus.PAUSED,
            "d" to DownloadStatus.DONE,
            "e" to DownloadStatus.FAILED,
        )
        assertEquals(2, queue.activeDownloadCount())
    }

    @Test
    fun 暂停的任务不算活动任务() {
        // 暂停后必须让出前台，否则用户按了暂停通知还挂着
        assertEquals(0, tasks("a" to DownloadStatus.PAUSED).activeDownloadCount())
    }

    @Test
    fun 全部为终态时活动数为零() {
        val queue = tasks("a" to DownloadStatus.DONE, "b" to DownloadStatus.FAILED)
        assertEquals(0, queue.activeDownloadCount())
    }

    @Test
    fun 没有上一帧快照时出现终态任务也算转换() {
        assertTrue(tasks("a" to DownloadStatus.DONE).hasTerminalTransitionFrom(emptyMap()))
        assertTrue(tasks("a" to DownloadStatus.FAILED).hasTerminalTransitionFrom(emptyMap()))
    }

    @Test
    fun 状态未变时不算转换() {
        val snapshot = tasks("a" to DownloadStatus.DONE)
        assertFalse(snapshot.hasTerminalTransitionFrom(snapshot))
    }

    @Test
    fun 从运行中转为完成算转换() {
        val previous = tasks("a" to DownloadStatus.RUNNING)
        assertTrue(tasks("a" to DownloadStatus.DONE).hasTerminalTransitionFrom(previous))
    }

    @Test
    fun 仍在排队或运行不算转换() {
        val previous = tasks("a" to DownloadStatus.QUEUED)
        assertFalse(tasks("a" to DownloadStatus.RUNNING).hasTerminalTransitionFrom(previous))
    }

    @Test
    fun 一个任务完成另一个仍在跑也算转换() {
        val previous = tasks("a" to DownloadStatus.RUNNING, "b" to DownloadStatus.RUNNING)
        val current = tasks("a" to DownloadStatus.DONE, "b" to DownloadStatus.RUNNING)
        assertTrue(current.hasTerminalTransitionFrom(previous))
    }

    @Test
    fun 暂停不算终态转换() {
        val previous = tasks("a" to DownloadStatus.RUNNING)
        assertFalse(tasks("a" to DownloadStatus.PAUSED).hasTerminalTransitionFrom(previous))
    }

    @Test
    fun 任务被取消移除后不产生终态转换() {
        // cancel 会把任务从 map 移除，不会留下 DONE；此时不该触发立即刷新
        val previous = tasks("a" to DownloadStatus.RUNNING)
        assertFalse(emptyMap<String, DownloadTask>().hasTerminalTransitionFrom(previous))
    }
}
