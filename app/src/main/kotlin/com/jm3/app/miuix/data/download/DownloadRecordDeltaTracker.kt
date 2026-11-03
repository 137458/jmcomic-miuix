package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadTask

/**
 * 上次落盘快照：仅记录关键判定属性。
 */
internal data class TaskPersistSnapshot(
    val status: DownloadStatus,
    val total: Int,
    val donePages: Int,
    val persistedAt: Long,
)

/**
 * 下载记录增量与防抖调度器：
 * 避免每一页下载完成都触发全量 SQLite upsert 写入放大。
 *
 * 核心策略：
 * 1. 结构变更立即落盘（新任务、状态转移、total 补齐）；
 * 2. 终态转换（DONE、FAILED、PAUSED）立即落盘且包含最终页码；
 * 3. 运行中单纯页码推进（index 变化）采用双重防抖窗口：
 *    - 距离上次落盘时间 >= [progressIntervalMs]（默认 1500ms）；
 *    - 或者页码推进步长 >= [progressStep]（默认 5 页）；
 * 4. 其它无变化的静态任务完全过滤，避免全表无谓写操作。
 */
class DownloadRecordDeltaTracker(
    val progressIntervalMs: Long = 1500L,
    val progressStep: Int = 5,
) {
    private val snapshots = mutableMapOf<String, TaskPersistSnapshot>()

    /**
     * 根据当前队列任务过滤出本次需要持久化落盘的任务子集。
     */
    fun filterTasksToPersist(
        currentTasks: Collection<DownloadTask>,
        nowMs: Long,
    ): List<DownloadTask> {
        val result = mutableListOf<DownloadTask>()
        for (task in currentTasks) {
            val last = snapshots[task.photoId]
            if (last == null) {
                // 首次见到该任务：必须立即建档落盘
                result.add(task)
            } else if (task.status != last.status) {
                // 状态转移（QUEUED -> RUNNING -> DONE / FAILED / PAUSED 等）：必须立即更新状态
                result.add(task)
            } else if (last.total <= 0 && task.total > 0) {
                // total 从未解析变为已知：必须立即更新总页数
                result.add(task)
            } else if (task.index != last.donePages) {
                // 仅页码推进：检查时间阈值或步长阈值
                val timePassed = nowMs - last.persistedAt >= progressIntervalMs
                val stepExceeded = (task.index - last.donePages) >= progressStep
                if (timePassed || stepExceeded) {
                    result.add(task)
                }
            }
        }
        return result
    }

    /**
     * 在持久化落盘完成后记录快照。
     */
    fun onPersisted(tasks: Collection<DownloadTask>, nowMs: Long) {
        for (task in tasks) {
            snapshots[task.photoId] = TaskPersistSnapshot(
                status = task.status,
                total = task.total,
                donePages = task.index,
                persistedAt = nowMs,
            )
        }
    }

    /**
     * 清理不再活跃（已取消、已删除）的任务快照，释放内存并允许同 photoId 重新入队时作为新任务识别。
     */
    fun pruneMissing(activePhotoIds: Set<String>) {
        snapshots.keys.retainAll(activePhotoIds)
    }
}
