package com.jm3.core.download

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 章节下载队列：
 * - 并发上限 [maxConcurrent]，任务完成自动续跑；
 * - 同 photoId 去重（FAILED 允许重新入队）；
 * - 单页失败重试 [maxRetries] 次后整任务 FAILED，不阻塞队列；
 * - 已落盘页自动跳过（断点续传）。
 *
 * 状态真源为内存 Map（锁保护）并投影到 [updates]；进度游标 [DownloadTask.index] 以页为单位推进。
 */
class DownloadQueue(
    private val fetcher: PageFetcher,
    private val store: DownloadStore,
    private val resolver: TaskResolver? = null,
    private val maxConcurrent: Int = 2,
    private val maxRetries: Int = 2,
) {

    private val _updates = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val updates: StateFlow<Map<String, DownloadTask>> = _updates.asStateFlow()

    private val work = Channel<Unit>(Channel.UNLIMITED)
    private val stateLock = Any()
    private var tasks: Map<String, DownloadTask> = mutableMapOf()
    private val started = AtomicBoolean(false)

    /** 启动 worker 池；幂等，重复调用忽略。 */
    suspend fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        repeat(maxConcurrent.coerceAtLeast(1)) { scope.launch { workerLoop() } }
    }

    /** 入队；同 photoId 已存在且未 FAILED 时拒绝并返回 false。 */
    fun enqueue(task: DownloadTask): Boolean {
        val accepted = synchronized(stateLock) {
            val existing = tasks[task.photoId]
            when {
                existing == null -> task
                existing.status == DownloadStatus.FAILED ->
                    existing.withStatus(DownloadStatus.QUEUED).copy(
                        albumId = task.albumId,
                        title = task.title,
                        total = task.total,
                        index = 0,
                    )
                else -> null
            }?.let { fresh ->
                tasks = tasks + (task.photoId to fresh)
                _updates.value = tasks
                true
            } ?: false
        }
        if (accepted) work.trySend(Unit)
        return accepted
    }

    /** 暂停（QUEUED/RUNNING -> PAUSED）；任务不存在时忽略。 */
    fun pause(photoId: String) {
        synchronized(stateLock) {
            val current = tasks[photoId] ?: return
            if (current.status == DownloadStatus.QUEUED || current.status == DownloadStatus.RUNNING) {
                tasks = tasks + (photoId to current.withStatus(DownloadStatus.PAUSED))
                _updates.value = tasks
            }
        }
    }

    /** 恢复（PAUSED -> QUEUED 并补跑）。 */
    fun resume(photoId: String) {
        val resumed = synchronized(stateLock) {
            val current = tasks[photoId] ?: return
            if (current.status == DownloadStatus.PAUSED) {
                tasks = tasks + (photoId to current.withStatus(DownloadStatus.QUEUED))
                _updates.value = tasks
                true
            } else {
                false
            }
        }
        if (resumed) work.trySend(Unit)
    }

    /**
     * 按外部任务快照恢复入队（进程重启后内存无任务，或失败重试）。
     * 快照状态须可合法转回 QUEUED（PAUSED/FAILED）；任务已存在时仅 PAUSED 可续跑，
     * 且沿用内存游标而非快照（持久化游标可能滞后），FAILED 则用快照游标重建重试；
     * 运行中/已完成返回 false，不打断在途任务。
     */
    fun restore(task: DownloadTask): Boolean {
        if (!task.status.canTransitionTo(DownloadStatus.QUEUED)) return false
        val accepted = synchronized(stateLock) {
            val existing = tasks[task.photoId]
            val restored = when {
                existing == null -> task.withStatus(DownloadStatus.QUEUED)
                existing.status == DownloadStatus.FAILED -> task.withStatus(DownloadStatus.QUEUED)
                existing.status == DownloadStatus.PAUSED -> existing.withStatus(DownloadStatus.QUEUED)
                else -> return false
            }
            tasks = tasks + (task.photoId to restored)
            _updates.value = tasks
            true
        }
        if (accepted) work.trySend(Unit)
        return accepted
    }

    /** 停机：关闭信号通道，worker 排空当前任务后退出；已入队任务保留在状态中。 */
    fun shutdown() {
        work.close()
    }

    /** 取消：直接移除任务，不做状态转移。 */
    fun cancel(photoId: String) {
        synchronized(stateLock) {
            tasks = tasks - photoId
            _updates.value = tasks
        }
    }

    private fun claimNext(): DownloadTask? = synchronized(stateLock) {
        val next = tasks.values.firstOrNull { it.status == DownloadStatus.QUEUED } ?: return null
        val running = next.withStatus(DownloadStatus.RUNNING)
        tasks = tasks + (next.photoId to running)
        _updates.value = tasks
        running
    }

    private suspend fun workerLoop() {
        for (tick in work) {
            val claimed = claimNext() ?: continue
            try {
                runTask(claimed)
            } catch (e: CancellationException) {
                // 协程取消必须向上传播，不能被当作单任务故障吞掉
                throw e
            } catch (e: Exception) {
                // 单任务故障隔离：置 FAILED 后继续消费后续任务，避免一个异常杀死 worker 导致队列永久卡死
                failWith(claimed)
            }
        }
    }

    private suspend fun runTask(claimed: DownloadTask) {
        var current = claimed
        if (current.total <= 0) {
            val resolved = resolver?.resolve(current.photoId)
            if (resolved == null || resolved.total <= 0) {
                failWith(current)
                return
            }
            current = update(current.photoId) { live ->
                live.copy(
                    albumId = resolved.albumId.ifBlank { live.albumId },
                    title = resolved.title.ifBlank { live.title },
                    total = resolved.total,
                )
            } ?: return
        }
        while (current.index < current.total) {
            val live = synchronized(stateLock) { tasks[current.photoId] } ?: return
            if (live.status != DownloadStatus.RUNNING) return // 已暂停/被替换
            val pageIndex = current.index + 1
            if (!store.exists(current.photoId, pageIndex)) {
                val bytes = fetchWithRetry(current.photoId, pageIndex)
                if (bytes == null) {
                    failWith(current)
                    return
                }
                store.write(current.photoId, pageIndex, bytes)
            }
            current = update(current.photoId) { it.copy(index = pageIndex) } ?: return
        }
        completeIfRunning(current.photoId)
    }

    /** 收尾：仍 RUNNING 才允许 DONE；下载中暂停保持 PAUSED，等 resume 后从游标继续补完。 */
    private fun completeIfRunning(photoId: String) {
        synchronized(stateLock) {
            val live = tasks[photoId] ?: return
            if (live.status != DownloadStatus.RUNNING) return
            tasks = tasks + (photoId to live.withStatus(DownloadStatus.DONE))
            _updates.value = tasks
        }
    }

    /** 单页重试：共 [maxRetries]+1 次机会，全败返回 null；取消直接向上传播，不消耗重试。 */
    private suspend fun fetchWithRetry(photoId: String, index: Int): ByteArray? {
        repeat(maxRetries + 1) {
            try {
                return fetcher.fetch(photoId, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单页取数失败：消耗一次重试机会
            }
        }
        return null
    }

    /** 仅 RUNNING 可置 FAILED；下载中暂停或已取消时保持原状态，避免非法转移崩溃。 */
    private fun failWith(stale: DownloadTask) {
        update(stale.photoId) { live ->
            if (live.status == DownloadStatus.RUNNING) live.withStatus(DownloadStatus.FAILED) else live
        }
    }

    /** 锁内按 photoId 应用变换；任务已不存在返回 null。status 以 Map 中现值为准，避免覆盖并发转移。 */
    private fun update(photoId: String, transform: (DownloadTask) -> DownloadTask): DownloadTask? =
        synchronized(stateLock) {
            val live = tasks[photoId] ?: return null
            val next = transform(live)
            tasks = tasks + (photoId to next)
            _updates.value = tasks
            next
        }
}
