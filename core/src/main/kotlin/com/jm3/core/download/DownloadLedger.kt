package com.jm3.core.download

import com.jm3.core.Jm3Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 台账条目：元数据 + 离线页列表 JSON（null 表示没缓存页列表，只能在线阅读）。 */
data class DownloadLedgerEntry(
    val record: DownloadRecord,
    val pagesJson: String?,
)

/**
 * 一条台账写入。
 *
 * 刻意分成两种形状，而不是「record + 可空 pagesJson」：[Progress] 根本没有 pagesJson 字段，
 * 于是「进度写把已缓存的页列表抹成 null」在类型上就不可表达——这类数据损坏不靠实现方自觉。
 */
sealed interface DownloadLedgerWrite {

    /** 本次要落库的记录。 */
    val record: DownloadRecord

    /** 进度/状态写入：只写元数据列，不得改动已存的离线页列表。 */
    data class Progress(override val record: DownloadRecord) : DownloadLedgerWrite

    /** 完成写入：连同离线页列表一起写，一条记录一次写全。 */
    data class Completed(
        override val record: DownloadRecord,
        val pagesJson: String,
    ) : DownloadLedgerWrite
}

/** 台账端口：一个写动词 + 整表读 + 单条读 + 删除。 */
interface DownloadLedgerStore {

    suspend fun put(write: DownloadLedgerWrite)

    /** 全量，按 updatedAt 倒序。 */
    suspend fun all(): List<DownloadRecord>

    suspend fun find(photoId: String): DownloadLedgerEntry?

    suspend fun delete(photoId: String)
}

/**
 * 下载台账：决定「哪条记录该在什么时候写」，并落到 [DownloadLedgerStore]。
 *
 * 合进来的三件事原本散在三处：防抖判定（app 侧 DownloadRecordDeltaTracker）、
 * 记录组装（app 侧 DownloadManager 的订阅体）、重启恢复（core 纯函数 + app 的循环）。
 * 现在只有一个入口 [record]：喂一份队列快照，它决定写不写、写什么。
 *
 * 队列仍是状态真源（[DownloadQueue.updates]）；台账只负责把真源翻译成记录，
 * 不做调度、不改状态机。
 */
class DownloadLedger(
    private val store: DownloadLedgerStore,
    /** 离线页列表来源：仅当本次会话真的拉过该 photo 的页列表时才给值。 */
    private val pagesJsonOf: (photoId: String) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    /** 单纯页码推进的最短落盘间隔。 */
    val progressIntervalMs: Long = 1500L,
    /** 单纯页码推进的最大容忍步长：超过就必须落盘，否则续传会退回更早的落点。 */
    val progressStep: Int = 5,
) {

    /** 上次落盘快照：只记判定用得上的三件事。 */
    private data class Snapshot(
        val status: DownloadStatus,
        val total: Int,
        val donePages: Int,
        val persistedAt: Long,
    )

    private val snapshots = mutableMapOf<String, Snapshot>()

    private val started = AtomicBoolean(false)

    /**
     * 订阅队列快照并持续落盘；幂等，重复订阅忽略。
     *
     * 订阅体只做一件事：把最新的任务集合交给 [record]。单条落库失败在 [record] 内部消化，
     * 订阅本身不会因为一次磁盘错误而死掉。
     */
    fun start(scope: CoroutineScope, updates: StateFlow<Map<String, DownloadTask>>) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            updates.collect { record(it.values) }
        }
    }

    /**
     * 喂一份任务快照；返回真正写了库的任务。
     *
     * 写入时机沿用既有的两级防抖（结构变更/终态立即写，页码推进按时间窗口或步长放行），
     * 但判定与落盘在同一个方法里，测试可以直接喂快照，不必等 StateFlow 合并。
     */
    suspend fun record(tasks: Collection<DownloadTask>, nowMs: Long = clock()): List<DownloadTask> {
        val persisted = mutableListOf<DownloadTask>()
        for (task in tasks) {
            if (!shouldPersist(task, nowMs)) continue
            try {
                store.put(writeOf(task))
                persisted += task
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单条落库失败不中断后续：失败的这条不记快照，下一次更新会立刻重试。
                Jm3Log.warn(TAG, "下载台账落库失败（photoId=" + task.photoId + "），本轮跳过", e)
            }
        }
        persisted.forEach { snapshots[it.photoId] = Snapshot(it.status, it.total, it.index, nowMs) }
        // 已消失的任务（取消/删除）不留快照，同 photoId 再入队才算新任务。
        snapshots.keys.retainAll(tasks.map { it.photoId }.toSet())
        return persisted
    }

    /**
     * 进程重启恢复：把上次进程被杀时遗留的 RUNNING/QUEUED 记录收敛为 PAUSED，返回收敛条数。
     *
     * 走 [DownloadLedgerWrite.Progress]，语句里没有页列表列，已缓存的离线页列表不会被动到。
     * 落库失败只影响展示态，不阻塞队列启动。
     */
    suspend fun recoverInterruptedRecords(): Int {
        val records = try {
            store.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Jm3Log.warn(TAG, "读取下载台账失败，跳过启动恢复", e)
            return 0
        }
        var converged = 0
        for ((record, recovered) in records.zip(recoverInterrupted(records))) {
            if (recovered.status == record.status) continue
            try {
                store.put(DownloadLedgerWrite.Progress(recovered.copy(updatedAt = clock())))
                converged++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Jm3Log.warn(TAG, "收敛中断记录失败（photoId=" + record.photoId + "）", e)
            }
        }
        return converged
    }

    suspend fun all(): List<DownloadRecord> = store.all()

    suspend fun find(photoId: String): DownloadLedgerEntry? = store.find(photoId)

    suspend fun delete(photoId: String) {
        store.delete(photoId)
    }

    /** 是否该为这次快照写库：结构变更与终态立即写，单纯页码推进受两道阈值约束。 */
    private fun shouldPersist(task: DownloadTask, nowMs: Long): Boolean {
        val last = snapshots[task.photoId] ?: return true
        if (task.status != last.status) return true
        if (last.total <= 0 && task.total > 0) return true
        if (task.index == last.donePages) return false
        val timePassed = nowMs - last.persistedAt >= progressIntervalMs
        val stepExceeded = (task.index - last.donePages) >= progressStep
        return timePassed || stepExceeded
    }

    /** 完成写只有在真的掌握页列表时才能写出；取不到就退化成进度写，绝不写 null 覆盖。 */
    private fun writeOf(task: DownloadTask): DownloadLedgerWrite {
        val record = DownloadRecord(
            photoId = task.photoId,
            albumId = task.albumId,
            title = task.title,
            total = task.total,
            donePages = task.index,
            status = task.status,
            updatedAt = clock(),
        )
        if (task.status != DownloadStatus.DONE) return DownloadLedgerWrite.Progress(record)
        val pages = pagesJsonOf(task.photoId) ?: return DownloadLedgerWrite.Progress(record)
        return DownloadLedgerWrite.Completed(record, pages)
    }

    private companion object {
        const val TAG = "DownloadLedger"
    }
}
