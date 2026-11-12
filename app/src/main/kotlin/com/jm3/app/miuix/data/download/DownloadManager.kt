package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadLedger
import com.jm3.core.download.DownloadLedgerEntry
import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.DownloadTask
import com.jm3.core.download.toResumeTask
import com.jm3.core.runCatchingSuspending
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 下载门面：启动队列、把队列接上台账、提供 UI 动作。
 *
 * 落盘策略与记录组装都在 [DownloadLedger]（:core，可测）；本类只剩"接线 + 文件删除 + 状态查询"。
 */
class DownloadManager internal constructor(
    val queue: DownloadQueue,
    private val fileStore: DownloadStore,
    private val ledger: DownloadLedger,
    private val appScope: CoroutineScope,
    private val onEvictPages: (String) -> Unit = {},
    private val onClearPages: () -> Unit = {},
    private val beforeRecordLock: suspend () -> Unit = {},
) {

    constructor(
        queue: DownloadQueue,
        fileStore: DownloadStore,
        ledger: DownloadLedger,
        appScope: CoroutineScope,
    ) : this(
        queue = queue,
        fileStore = fileStore,
        ledger = ledger,
        appScope = appScope,
        onEvictPages = {},
        onClearPages = {},
        beforeRecordLock = {},
    )

    private val started = AtomicBoolean(false)

    /** 串行化台账恢复/记录写入与删除动作，防止已删除条目被在途写入复活。 */
    private val ledgerLock = Mutex()

    /** 幂等启动：等待中断记录恢复完成后再启动 worker 池与台账订阅，避免新入队任务被覆盖为 PAUSED。 */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            // 恢复中断记录只是台账修复，失败也必须启动 worker；否则整条下载链路永久停摆
            ledgerLock.withLock {
                runCatchingSuspending { ledger.recoverInterruptedRecords() }
            }
            queue.start(appScope)
            queue.updates.collect {
                beforeRecordLock()
                ledgerLock.withLock {
                    // 在锁内重读最新队列快照，避免等待锁期间已被 remove/clearAll 删除的任务由旧快照写回复活。
                    val latest = queue.updates.value.values
                    runCatchingSuspending { ledger.record(latest) }
                }
            }
        }
    }

    /**
     * 章节入队（total 未知，开跑前由 resolver 补齐）。
     *
     * 返回是否真的接受了新任务：已在队列/运行中/已完成的章节会返回 false，
     * UI 据此区分「已加入」和「已在下载队列或已下载」，不能把 false 当成静默成功。
     */
    fun enqueueChapter(photoId: String, albumId: String, title: String): Boolean {
        ensureStarted()
        // 标题可能为空（详情页未拿到章节名）：空串会让书架/任务列表出现无文字的空行。
        return queue.enqueue(
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

    /**
     * 删除：取消任务 + 清文件 + 清记录。
     *
     * 递归删章节目录是阻塞 IO，必须切到 IO 线程，不能拖住点击所在的主线程；
     * 与台账写入共用 [ledgerLock]，避免在途 record() 在删除之后将记录写回数据库。
     */
    suspend fun remove(photoId: String) {
        queue.cancel(photoId)
        ledgerLock.withLock {
            queue.cancel(photoId)
            withContext(Dispatchers.IO) {
                fileStore.delete(photoId)
                ledger.delete(photoId)
            }
            onEvictPages(photoId)
        }
    }

    /** 清空全部下载：同时覆盖内存队列与已落库台账，确保尚未落盘的任务也会被取消与清理。 */
    suspend fun clearAll() {
        val memoryIds = queue.updates.value.keys.toList()
        memoryIds.forEach { queue.cancel(it) }
        ledgerLock.withLock {
            val currentMemoryIds = queue.updates.value.keys.toList()
            val persistedIds = ledger.all().map { it.photoId }
            val allIds = (memoryIds + currentMemoryIds + persistedIds).distinct()
            allIds.forEach { queue.cancel(it) }
            withContext(Dispatchers.IO) {
                allIds.forEach { photoId ->
                    fileStore.delete(photoId)
                    ledger.delete(photoId)
                }
            }
            onClearPages()
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
            .filter { it.status.isActive }
            .forEach { queue.pause(it.photoId) }
    }
}

/** 空标题兜底文案：:core 层无 Context，取不到 string 资源，只能在此固定。 */
private const val UNTITLED_CHAPTER_TITLE = "未命名章节"

/**
 * 队列专用存储包装：worker 从网络取回页字节后，若任务已被取消/移除，阻止其再向磁盘写入残留文件。
 */
internal class QueueGuardedDownloadStore(
    private val delegate: DownloadStore,
    private val isActiveInQueue: (photoId: String) -> Boolean,
) : DownloadStore by delegate {
    override fun write(photoId: String, index: Int, bytes: ByteArray) {
        if (!isActiveInQueue(photoId)) return
        delegate.write(photoId, index, bytes)
        // 写完后再检查一次：若写盘期间任务刚好被 cancel，清掉刚落盘的孤儿目录
        if (!isActiveInQueue(photoId)) delegate.delete(photoId)
    }
}

