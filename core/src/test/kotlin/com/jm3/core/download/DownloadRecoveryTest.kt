package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.FAILED
import com.jm3.core.download.DownloadStatus.PAUSED
import com.jm3.core.download.DownloadStatus.QUEUED
import com.jm3.core.download.DownloadStatus.RUNNING
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadRecoveryTest {

    private fun record(photoId: String, status: DownloadStatus, donePages: Int = 1) = DownloadRecord(
        photoId = photoId,
        albumId = "a-$photoId",
        title = "t-$photoId",
        total = 5,
        donePages = donePages,
        status = status,
        updatedAt = 100L,
    )

    @Test
    fun `interrupted records converge to paused and keep other fields`() {
        val records = listOf(
            record("running", RUNNING, donePages = 2),
            record("queued", QUEUED, donePages = 0),
            record("paused", PAUSED, donePages = 1),
            record("done", DONE, donePages = 5),
            record("failed", FAILED, donePages = 2),
        )
        val recovered = recoverInterrupted(records)
        assertEquals(listOf(PAUSED, PAUSED, PAUSED, DONE, FAILED), recovered.map { it.status })
        // 除 status 外字段与顺序保持不变
        val expected = records.map { it.copy(status = if (it.status == RUNNING || it.status == QUEUED) PAUSED else it.status) }
        assertEquals(expected, recovered)
    }

    @Test
    fun `terminal and paused records are unchanged and mapping is idempotent`() {
        val settled = listOf(record("done", DONE, donePages = 5), record("failed", FAILED))
        assertEquals(settled, recoverInterrupted(settled))
        assertEquals(emptyList<DownloadRecord>(), recoverInterrupted(emptyList()))
        // 重复恢复保持同一结果（启动恢复可安全重入）
        val interrupted = listOf(record("running", RUNNING), record("queued", QUEUED))
        val once = recoverInterrupted(interrupted)
        assertEquals(once, recoverInterrupted(once))
    }

    @Test
    fun `paused record maps to resumed task from donePages`() {
        val paused = record("paused", PAUSED, donePages = 2)
        val task = paused.toResumeTask()
        // 游标沿用已完成页数，从下一页续传；元数据字段原样带过去
        assertEquals(
            DownloadTask(
                photoId = "paused",
                albumId = "a-paused",
                title = "t-paused",
                total = 5,
                index = 2,
                status = PAUSED,
            ),
            task,
        )
    }

    @Test
    fun `failed record maps to paused retry snapshot`() {
        val failed = record("failed", FAILED, donePages = 1)
        val task = failed.toResumeTask()
        // 失败重试同样从 donePages 续传，不回到第 0 页重下
        assertEquals(PAUSED, task.status)
        assertEquals(1, task.index)
        assertEquals(5, task.total)
    }

    @Test
    fun `done record is not resurrected`() {
        val done = record("done", DONE, donePages = 5)
        // 终态不参与恢复：保留 DONE，队列按转移表拒绝重新入队
        assertEquals(DONE, done.toResumeTask().status)
        assertEquals(5, done.toResumeTask().index)
    }
}
