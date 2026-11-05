package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.FAILED
import com.jm3.core.download.DownloadStatus.PAUSED
import com.jm3.core.download.DownloadStatus.QUEUED
import com.jm3.core.download.DownloadStatus.RUNNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 状态能力的单测：活动 / 落定 / 可重排 / 恢复快照四件事只有一处定义（[DownloadStatus]），
 * 队列、台账记录、通知统计一律调用，不再各自拼 Set。
 */
class DownloadStatusSemanticsTest {

    @Test
    fun `活动状态恰好是排队中与运行中`() {
        assertEquals(listOf(QUEUED, RUNNING), DownloadStatus.entries.filter { it.isActive })
    }

    @Test
    fun `暂停不属于落定状态`() {
        // 暂停还能续传，把它算终态会让通知与台账都误判「这条已经结束了」
        assertFalse(PAUSED.isFinished)
        assertTrue(DONE.isFinished)
        assertTrue(FAILED.isFinished)
        assertFalse(QUEUED.isFinished)
        assertFalse(RUNNING.isFinished)
    }

    @Test
    fun `可重排只覆盖暂停与失败`() {
        assertTrue(PAUSED.canRequeue)
        assertTrue(FAILED.canRequeue)
        // 已完成重排等于重下整章；已在队列/运行中的不存在「重排」动作
        assertFalse(DONE.canRequeue)
        assertFalse(QUEUED.canRequeue)
        assertFalse(RUNNING.canRequeue)
    }

    @Test
    fun `恢复快照把在途状态压成暂停而完成保持完成`() {
        assertEquals(PAUSED, QUEUED.resumeSnapshot)
        assertEquals(PAUSED, RUNNING.resumeSnapshot)
        assertEquals(PAUSED, PAUSED.resumeSnapshot)
        assertEquals(PAUSED, FAILED.resumeSnapshot)
        // DONE 原样交回：队列按转移表拒绝复活，不会重下已完成的章节
        assertEquals(DONE, DONE.resumeSnapshot)
    }

    @Test
    fun `能力与转移表口径一致`() {
        // 活动 = 可暂停；可重排 = 可回队列。表格改了而能力没跟上时这条会红
        for (status in DownloadStatus.entries) {
            assertEquals(status.canTransitionTo(PAUSED), status.isActive, "isActive 与转移表不一致：$status")
            assertEquals(status.canTransitionTo(QUEUED), status.canRequeue, "canRequeue 与转移表不一致：$status")
        }
    }

    @Test
    fun `合法转移组合`() {
        val legal = listOf(
            QUEUED to RUNNING,
            QUEUED to PAUSED,
            RUNNING to PAUSED,
            RUNNING to DONE,
            RUNNING to FAILED,
            PAUSED to QUEUED,
            FAILED to QUEUED,
        )
        for ((from, to) in legal) assertTrue(from.canTransitionTo(to), "应允许 $from -> $to")
    }

    @Test
    fun `非法转移组合`() {
        val illegal = listOf(
            QUEUED to DONE,
            QUEUED to FAILED,
            QUEUED to QUEUED,
            RUNNING to QUEUED,
            RUNNING to RUNNING,
            PAUSED to RUNNING,
            PAUSED to DONE,
            PAUSED to FAILED,
            FAILED to RUNNING,
            FAILED to DONE,
            DONE to QUEUED,
            DONE to RUNNING,
            DONE to FAILED,
        )
        for ((from, to) in illegal) assertFalse(from.canTransitionTo(to), "应拒绝 $from -> $to")
    }
}
