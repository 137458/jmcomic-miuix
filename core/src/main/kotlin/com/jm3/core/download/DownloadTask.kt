package com.jm3.core.download

/** 下载任务状态。 */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    DONE,
    FAILED,
    ;

    /** 合法状态转移表；非法组合返回 false。 */
    fun canTransitionTo(next: DownloadStatus): Boolean = when (this) {
        QUEUED -> next == RUNNING || next == PAUSED
        RUNNING -> next == PAUSED || next == DONE || next == FAILED
        PAUSED -> next == QUEUED
        FAILED -> next == QUEUED
        DONE -> false
    }
}

/**
 * 章节下载任务：index 为进度游标（已完成页数，页号 1..total）。
 * total 未知时允许以 0 入队，由队列在开跑前补齐。
 */
data class DownloadTask(
    val photoId: String,
    val albumId: String,
    val title: String,
    val total: Int,
    val index: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
) {
    /** 按转移表推进状态；非法转移抛 [IllegalStateException]。 */
    fun withStatus(next: DownloadStatus): DownloadTask {
        check(status.canTransitionTo(next)) { "非法状态转移：$status -> $next" }
        return copy(status = next)
    }
}
