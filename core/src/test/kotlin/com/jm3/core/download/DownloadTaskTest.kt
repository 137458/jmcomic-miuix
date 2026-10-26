package com.jm3.core.download

import com.jm3.core.download.DownloadStatus.DONE
import com.jm3.core.download.DownloadStatus.FAILED
import com.jm3.core.download.DownloadStatus.PAUSED
import com.jm3.core.download.DownloadStatus.QUEUED
import com.jm3.core.download.DownloadStatus.RUNNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DownloadTaskTest {

    private fun task() = DownloadTask(photoId = "1", albumId = "a", title = "t", total = 3)

    @Test
    fun `legal transitions return new task`() {
        assertEquals(RUNNING, task().withStatus(RUNNING).status)
        assertEquals(PAUSED, task().withStatus(PAUSED).status)
        val running = task().withStatus(RUNNING)
        assertEquals(DONE, running.withStatus(DONE).status)
        assertEquals(FAILED, running.withStatus(FAILED).status)
        assertEquals(QUEUED, running.withStatus(PAUSED).withStatus(QUEUED).status)
        assertEquals(QUEUED, running.withStatus(FAILED).withStatus(QUEUED).status)
    }

    @Test
    fun `illegal transitions throw`() {
        // QUEUED 不能直达 DONE/FAILED，不能自转移
        assertFailsWith<IllegalStateException> { task().withStatus(DONE) }
        assertFailsWith<IllegalStateException> { task().withStatus(FAILED) }
        assertFailsWith<IllegalStateException> { task().withStatus(QUEUED) }
        // DONE 是终态
        val done = task().withStatus(RUNNING).withStatus(DONE)
        assertFailsWith<IllegalStateException> { done.withStatus(RUNNING) }
        assertFailsWith<IllegalStateException> { done.withStatus(FAILED) }
        // PAUSED 只能回 QUEUED
        val paused = task().withStatus(RUNNING).withStatus(PAUSED)
        assertFailsWith<IllegalStateException> { paused.withStatus(RUNNING) }
        assertFailsWith<IllegalStateException> { paused.withStatus(DONE) }
    }
}
