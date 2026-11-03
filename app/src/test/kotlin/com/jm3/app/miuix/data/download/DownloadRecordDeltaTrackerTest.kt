package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DownloadRecordDeltaTracker] 单元测试：
 * 验证下载任务持久化调度器的增量判定与防抖机制，
 * 避免每一页下载均引发全量 SQLite upsert 写入放大。
 */
class DownloadRecordDeltaTrackerTest {

    private fun sampleTask(
        photoId: String = "p1",
        albumId: String = "a1",
        title: String = "Chapter 1",
        total: Int = 100,
        index: Int = 0,
        status: DownloadStatus = DownloadStatus.QUEUED,
    ) = DownloadTask(photoId, albumId, title, total, index, status)

    @Test
    fun 首次出现的任务必须立即落盘() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val task = sampleTask(photoId = "p1", status = DownloadStatus.QUEUED)
        
        val toPersist = tracker.filterTasksToPersist(listOf(task), nowMs = 1000L)
        assertEquals(listOf(task), toPersist)
    }

    @Test
    fun 状态改变必须立即落盘且不受时间间隔约束() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", status = DownloadStatus.QUEUED)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 仅过了 50ms，但状态从 QUEUED 转为 RUNNING
        val t2 = t1.copy(status = DownloadStatus.RUNNING)
        val toPersist = tracker.filterTasksToPersist(listOf(t2), nowMs = 1050L)
        assertEquals(listOf(t2), toPersist)
    }

    @Test
    fun total从未解析变为已知必须立即落盘() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 0, status = DownloadStatus.RUNNING)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 仅过了 10ms，total 从 0 解析为 50
        val t2 = t1.copy(total = 50)
        val toPersist = tracker.filterTasksToPersist(listOf(t2), nowMs = 1010L)
        assertEquals(listOf(t2), toPersist)
    }

    @Test
    fun 进度推进在时间与步长阈值内时跳过落盘() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 100, index = 0, status = DownloadStatus.RUNNING)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 仅过了 100ms，index 从 0 进到 2（步长 2 < 5，时间 100ms < 1500ms）
        val t2 = t1.copy(index = 2)
        val toPersist = tracker.filterTasksToPersist(listOf(t2), nowMs = 1100L)
        assertTrue(toPersist.isEmpty(), "阈值内的微小进度步进应跳过落盘，避免 SQLite 频繁争锁")
    }

    @Test
    fun 进度推进达到步长阈值时放行落盘() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 100, index = 0, status = DownloadStatus.RUNNING)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 时间只过了 200ms，但 index 步进了 5 页（0 -> 5 >= progressStep）
        val t2 = t1.copy(index = 5)
        val toPersist = tracker.filterTasksToPersist(listOf(t2), nowMs = 1200L)
        assertEquals(listOf(t2), toPersist)
    }

    @Test
    fun 进度推进达到时间间隔阈值时放行落盘() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 100, index = 0, status = DownloadStatus.RUNNING)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 虽然步长只前进了 1 页（0 -> 1），但时间过去了 1500ms（达到了 progressIntervalMs）
        val t2 = t1.copy(index = 1)
        val toPersist = tracker.filterTasksToPersist(listOf(t2), nowMs = 2500L)
        assertEquals(listOf(t2), toPersist)
    }

    @Test
    fun 无任何变化的静态任务不应落盘() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 100, index = 10, status = DownloadStatus.RUNNING)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 时间即使过去 5000ms，但任务状态和进度毫无变化
        val toPersist = tracker.filterTasksToPersist(listOf(t1), nowMs = 6000L)
        assertTrue(toPersist.isEmpty(), "完全未发生变化的静态任务不应触发写库")
    }

    @Test
    fun 多任务并发时只筛选发生实质变化的受影响任务() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 100, index = 0, status = DownloadStatus.RUNNING)
        val t2 = sampleTask(photoId = "p2", total = 80, index = 0, status = DownloadStatus.QUEUED)
        tracker.onPersisted(listOf(t1, t2), nowMs = 1000L)

        // t1 仅仅推进 1 页且在时间窗口内（应跳过）
        // t2 突然转为 RUNNING（应落盘）
        val updatedT1 = t1.copy(index = 1)
        val updatedT2 = t2.copy(status = DownloadStatus.RUNNING)

        val toPersist = tracker.filterTasksToPersist(listOf(updatedT1, updatedT2), nowMs = 1100L)
        assertEquals(listOf(updatedT2), toPersist, "多任务并发下只应持久化状态实质变化的任务")
    }

    @Test
    fun 终态转换为DONE必须立即落盘最新页码() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", total = 100, index = 98, status = DownloadStatus.RUNNING)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 终态 DONE，index 到 100，距上次仅 50ms 且步长仅 2
        val t2 = t1.copy(index = 100, status = DownloadStatus.DONE)
        val toPersist = tracker.filterTasksToPersist(listOf(t2), nowMs = 1050L)
        assertEquals(listOf(t2), toPersist)
    }

    @Test
    fun 任务移除后从Tracker清理快照() {
        val tracker = DownloadRecordDeltaTracker(progressIntervalMs = 1500L, progressStep = 5)
        val t1 = sampleTask(photoId = "p1", status = DownloadStatus.QUEUED)
        tracker.onPersisted(listOf(t1), nowMs = 1000L)

        // 移除 p1
        tracker.pruneMissing(activePhotoIds = emptySet())

        // 再次加入 p1（如同全新任务），应当再次作为新任务放行
        val toPersist = tracker.filterTasksToPersist(listOf(t1), nowMs = 1050L)
        assertEquals(listOf(t1), toPersist)
    }
}
