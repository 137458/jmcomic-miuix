package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadLedger
import com.jm3.core.download.DownloadLedgerEntry
import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.DownloadTask
import com.jm3.core.download.toResumeTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 下载门面：启动队列、把队列接上台账、提供 UI 动作。
 *
 * 落盘策略与记录组装都在 [DownloadLedger]（:core，可测）；本类只剩"接线 + 文件删除 + 状态查询"。
 */
class DownloadManager(
    val queue: DownloadQueue,
    private val fileStore: DownloadStore,
    private val ledger: DownloadLedger,
    private val appScope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    /** 幂等启动：中断记录恢复 + worker 池 + 台账订阅。 */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            // 先收敛上次进程被杀遗留的中断记录，再启动 worker
            ledger.recoverInterruptedRecords()
            queue.start(appScope)
        }
        ledger.start(appScope, queue.updates)
    }

    /** 章节入队（total 未知，开跑前由 resolver 补齐）。 */
    fun enqueueChapter(photoId: String, albumId: String, title: String) {
        ensureStarted()
        // 标题可能为空（详情页未拿到章节名）：空串会让书架/任务列表出现无文字的空行。
        queue.enqueue(
            DownloadTask(
                photoId = photoId,
                albumId = albumId,
                title = title.ifBlank { UNTITLED_CHAPTER_TITLE },
                total = 0,
            ),
        )
    }

    suspend fun records(): List<DownloadRecord> = ledger.all()

    suspend fun find(photoId: String): DownloadLedgerEntry? = ledger.find(photoId)

    fun pause(photoId: String) = queue.pause(photoId)

    /**
     * 按持久化记录恢复入队：内存无任务时用记录快照重建，从 donePages 续传；
     * 已暂停任务直接续跑（沿用内存游标），运行中/已完成返回 false。
     */
    fun resumeRecord(record: DownloadRecord): Boolean {
        ensureStarted()
        return queue.restore(record.toResumeTask())
    }

    /** 删除：取消任务 + 清文件 + 清记录。 */
    fun remove(photoId: String) {
        queue.cancel(photoId)
        fileStore.delete(photoId)
        appScope.launch { ledger.delete(photoId) }
    }

    /** 清空全部下载：取消在途任务 + 删文件 + 删记录（文件与记录双清）。 */
    suspend fun clearAll() {
        val all = ledger.all()
        all.forEach { queue.cancel(it.photoId) }
        all.forEach { record ->
            fileStore.delete(record.photoId)
            ledger.delete(record.photoId)
        }
    }

    /**
     * 把全部在途任务置 PAUSED（QUEUED / RUNNING）。
     *
     * 供 dataSync 前台服务的 onTimeout 使用：Android 15+ 对 dataSync 前台服务有累计
     * 6 小时配额，用尽后系统回调 onTimeout，此时必须让出前台，任务转暂停等待用户续传，
     * 而不是被系统直接杀掉留下半截记录。不动 :core 的队列实现，只调公开的 pause。
     *
     * 这里只改状态、不发通知：本层拿不到 Context，可见的「已暂停」提示由
     * [DownloadService] 在超时处理里发布。
     */
    fun pauseActive() {
        queue.updates.value.values
            .filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
            .forEach { queue.pause(it.photoId) }
    }
}

/** 空标题兜底文案：:core 层无 Context，取不到 string 资源，只能在此固定。 */
private const val UNTITLED_CHAPTER_TITLE = "未命名章节"

/** 活动任务数（QUEUED + RUNNING）：前台服务生命周期的唯一驱动量。 */
internal fun Map<String, DownloadTask>.activeDownloadCount(): Int =
    values.count { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }

/**
 * 是否有任务从在途变为 DONE/FAILED。
 *
 * 这类终态转换要让通知立即刷新（不受进度节流限制），否则最后一条进度会一直停在
 * N-1 页、用户以为下载卡住。
 */
internal fun Map<String, DownloadTask>.hasTerminalTransitionFrom(
    previous: Map<String, DownloadTask>,
): Boolean = values.any { task ->
    val terminal = task.status == DownloadStatus.DONE || task.status == DownloadStatus.FAILED
    terminal && previous[task.photoId]?.status != task.status
}
