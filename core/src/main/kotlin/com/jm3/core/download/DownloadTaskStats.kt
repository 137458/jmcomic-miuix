package com.jm3.core.download

/** 活动任务数（QUEUED + RUNNING）：前台服务生命周期的唯一驱动量。 */
fun Map<String, DownloadTask>.activeDownloadCount(): Int = values.count { it.status.isActive }

/**
 * 是否有任务从在途变为 DONE/FAILED。
 *
 * 这类终态转换要让通知立即刷新（不受进度节流限制），否则最后一条进度会一直停在
 * N-1 页、用户以为下载卡住。
 */
fun Map<String, DownloadTask>.hasTerminalTransitionFrom(
    previous: Map<String, DownloadTask>,
): Boolean = values.any { task ->
    val terminal = task.status.isFinished
    terminal && previous[task.photoId]?.status != task.status
}
