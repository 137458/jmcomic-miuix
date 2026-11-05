package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.FAILED
import com.jm3.core.download.DownloadStatus.PAUSED
import com.jm3.core.download.DownloadStatus.QUEUED
import com.jm3.core.download.DownloadStatus.RUNNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 通知展示快照的单测：前台通知显示哪条任务、什么文案，全部由 [present] 决定。
 * 列表行（书架）走 [presentRow] 逐条取标签与动作，是另一个 module。
 */
class DownloadPresentationTest {

    private fun task(
        photoId: String,
        status: DownloadStatus,
        index: Int = 0,
        total: Int = 12,
    ) = DownloadTask(
        photoId = photoId,
        albumId = "a-$photoId",
        title = "章节 $photoId",
        total = total,
        index = index,
        status = status,
    )

    private fun snapshot(vararg tasks: DownloadTask): Map<String, DownloadTask> = tasks.associateBy { it.photoId }

    @Test
    fun `运行中的任务带确定进度展示`() {
        val presentation = present(snapshot(task("a", RUNNING, index = 3)))
        assertNotNull(presentation)
        assertEquals("a", presentation.task.photoId)
        assertEquals(1, presentation.activeCount)
        assertEquals(DownloadTitle.Single, presentation.title)
        assertEquals(DownloadText.Progress("章节 a", 3, 12), presentation.text)
        assertEquals(DownloadShortText.Progress(3, 12), presentation.shortText)
        assertEquals(DownloadMeter.Determinate(3, 12), presentation.meter)
    }

    @Test
    fun `两条活动任务时标题报总数并优先展示运行中`() {
        val presentation = present(snapshot(task("a", QUEUED), task("b", RUNNING, index = 5)))
        assertNotNull(presentation)
        assertEquals("b", presentation.task.photoId)
        assertEquals(2, presentation.activeCount)
        assertEquals(DownloadTitle.Multi(2), presentation.title)
    }

    @Test
    fun `元数据未解析时正文与胶囊走待定分支`() {
        val presentation = present(snapshot(task("a", RUNNING, index = 0, total = 0)))
        assertNotNull(presentation)
        assertEquals(DownloadText.Pending("章节 a"), presentation.text)
        assertEquals(DownloadShortText.Pending, presentation.shortText)
        assertEquals(DownloadMeter.Indeterminate, presentation.meter)
    }

    @Test
    fun `进度越界时按总量夹取`() {
        // 续传快照的 donePages 可能大于重新解析出的 total，展示侧不能出现「13/12」
        val presentation = present(snapshot(task("a", RUNNING, index = 13, total = 12)))
        assertNotNull(presentation)
        assertEquals(DownloadText.Progress("章节 a", 12, 12), presentation.text)
        assertEquals(DownloadShortText.Progress(12, 12), presentation.shortText)
        assertEquals(DownloadMeter.Determinate(12, 12), presentation.meter)
    }

    @Test
    fun `暂停与已完成的任务不进前台通知`() {
        assertNull(present(snapshot(task("a", PAUSED, index = 3))))
        assertNull(present(snapshot(task("a", DONE, index = 12))))
    }

    @Test
    fun `空快照没有可展示任务`() {
        assertNull(present(emptyMap()))
        assertNull(present(snapshot(task("a", PAUSED, index = 3))))
    }
}
