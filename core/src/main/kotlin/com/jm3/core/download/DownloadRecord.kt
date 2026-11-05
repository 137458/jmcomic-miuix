package com.jm3.core.download

/** 本地书架记录（下载元数据）。 */
data class DownloadRecord(
    val photoId: String,
    val albumId: String,
    val title: String,
    val total: Int,
    val donePages: Int,
    val status: DownloadStatus,
    val updatedAt: Long,
)

/**
 * 进程重启恢复：把上次进程被杀时遗留的在途记录（[DownloadStatus.isActive]，即 RUNNING/QUEUED）
 * 收敛为 PAUSED。这两类记录已无内存任务承载，保持原状态会让 UI 误以为仍在下载；
 * 落定或已暂停的记录保持不变。
 */
fun recoverInterrupted(records: List<DownloadRecord>): List<DownloadRecord> = records.map { record ->
    if (record.status.isActive) record.copy(status = DownloadStatus.PAUSED) else record
}

/**
 * 从持久化记录重建可恢复的队列任务：游标从 [DownloadRecord.donePages] 续传，已落盘页由队列跳过。
 * 状态取 [DownloadStatus.resumeSnapshot]：除 DONE 外一律按暂停快照交给队列恢复
 * （PAUSED/FAILED -> QUEUED 为合法转移）；DONE 保留原状态，由队列按转移表拒绝复活。
 */
fun DownloadRecord.toResumeTask(): DownloadTask = DownloadTask(
    photoId = photoId,
    albumId = albumId,
    title = title,
    total = total,
    index = donePages,
    status = status.resumeSnapshot,
)
