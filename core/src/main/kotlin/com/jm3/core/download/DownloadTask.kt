package com.jm3.core.download

/** 下载任务状态。 */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    DONE,
    FAILED,
    ;

    /** 占用前台并且会自动推进：活动任务数、暂停条件、启动恢复都只看这一个判定。 */
    val isActive: Boolean get() = this == QUEUED || this == RUNNING

    /** 已落定：不再自动推进，且要让通知绕过节流立刻刷新（PAUSED 可续传，不算落定）。 */
    val isFinished: Boolean get() = this == DONE || this == FAILED

    /** 可交回队列重新排队（用户续传 / 失败重试）；等价于「能合法转到 QUEUED」。 */
    val canRequeue: Boolean get() = canTransitionTo(QUEUED)

    /** 进程重启后交给队列的恢复状态：DONE 原样交回（队列按转移表拒绝复活），其余压成 PAUSED。 */
    val resumeSnapshot: DownloadStatus get() = if (this == DONE) DONE else PAUSED

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
