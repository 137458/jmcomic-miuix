package com.jm3.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 行级展示选型（书架、详情行）的意图：
 * - 标签是 [DownloadStatus] 到展示层标签的唯一映射，五种状态一一对应；
 * - 主操作由状态决定，失败与暂停必须是两个不同动作（重试 vs 继续）；
 * - 进度条只在「有总量且未完成」时出现，DONE 之后不再画；
 * - 越界进度按总量夹取，避免 LinearProgressIndicator 收到大于 1 的比例。
 */
class DownloadRowTest {

    private fun record(
        status: DownloadStatus,
        total: Int = 12,
        done: Int = 3,
    ) = DownloadRecord(
        photoId = "p1",
        albumId = "a1",
        title = "章节",
        total = total,
        donePages = done,
        status = status,
        updatedAt = 0L,
    )

    @Test
    fun `五种状态各有独立标签`() {
        assertEquals(5, DownloadStatus.entries.map { it.label() }.toSet().size)
        assertEquals(DownloadLabel.Queued, DownloadStatus.QUEUED.label())
        assertEquals(DownloadLabel.Running, DownloadStatus.RUNNING.label())
        assertEquals(DownloadLabel.Paused, DownloadStatus.PAUSED.label())
        assertEquals(DownloadLabel.Done, DownloadStatus.DONE.label())
        assertEquals(DownloadLabel.Failed, DownloadStatus.FAILED.label())
    }

    @Test
    fun `已完成只有打开动作且不再画进度条`() {
        val row = presentRow(record(DownloadStatus.DONE))
        assertEquals(DownloadAction.Open, row.action)
        assertEquals(DownloadLabel.Done, row.label)
        assertFalse(row.showsProgressBar)
    }

    @Test
    fun `失败走重试而暂停走续传`() {
        assertEquals(DownloadAction.Retry, presentRow(record(DownloadStatus.FAILED)).action)
        assertEquals(DownloadAction.Resume, presentRow(record(DownloadStatus.PAUSED)).action)
    }

    @Test
    fun `排队与运行中都是暂停动作`() {
        assertEquals(DownloadAction.Pause, presentRow(record(DownloadStatus.QUEUED)).action)
        assertEquals(DownloadAction.Pause, presentRow(record(DownloadStatus.RUNNING)).action)
    }

    @Test
    fun `未完成的四种状态都画进度条`() {
        DownloadStatus.entries.filter { it != DownloadStatus.DONE }.forEach { status ->
            assertTrue(presentRow(record(status)).showsProgressBar, "应画进度条：$status")
        }
    }

    @Test
    fun `进度越界按总量夹取`() {
        val row = presentRow(record(DownloadStatus.RUNNING, total = 12, done = 13))
        assertEquals(12, row.donePages)
        assertEquals(12, row.total)
    }

    @Test
    fun `元数据未解析时不画进度条`() {
        val row = presentRow(record(DownloadStatus.QUEUED, total = 0, done = 0))
        assertFalse(row.showsProgressBar)
        assertEquals(0, row.donePages)
    }
}
