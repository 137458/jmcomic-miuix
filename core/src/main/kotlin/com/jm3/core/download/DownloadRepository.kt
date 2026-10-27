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

/** 下载元数据持久化：实现方负责落库。 */
interface DownloadRepository {
    suspend fun upsert(record: DownloadRecord)

    /** 全量，按 updatedAt 倒序。 */
    suspend fun all(): List<DownloadRecord>

    suspend fun delete(photoId: String)
}

/**
 * 进程重启恢复：把上次进程被杀时遗留的 RUNNING/QUEUED 记录收敛为 PAUSED。
 * 这两类记录已无内存任务承载，保持原状态会让 UI 误以为仍在下载；
 * PAUSED/DONE/FAILED 保持不变（已暂停或终态）。
 */
fun recoverInterrupted(records: List<DownloadRecord>): List<DownloadRecord> = records.map { record ->
    when (record.status) {
        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> record.copy(status = DownloadStatus.PAUSED)
        DownloadStatus.PAUSED, DownloadStatus.DONE, DownloadStatus.FAILED -> record
    }
}

/**
 * 从持久化记录重建可恢复的队列任务：游标从 [DownloadRecord.donePages] 续传，已落盘页由队列跳过。
 * 除终态 DONE 外一律按暂停快照交给队列恢复（PAUSED/FAILED -> QUEUED 为合法转移）；
 * DONE 保留原状态，由队列按转移表拒绝复活。
 */
fun DownloadRecord.toResumeTask(): DownloadTask = DownloadTask(
    photoId = photoId,
    albumId = albumId,
    title = title,
    total = total,
    index = donePages,
    status = if (status == DownloadStatus.DONE) DownloadStatus.DONE else DownloadStatus.PAUSED,
)

/** 纯内存实现，供测试与替换。 */
class InMemoryDownloadRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) : DownloadRepository {

    private val records = LinkedHashMap<String, DownloadRecord>()

    override suspend fun upsert(record: DownloadRecord) {
        records[record.photoId] = record.copy(updatedAt = clock())
    }

    override suspend fun all(): List<DownloadRecord> = records.values.sortedByDescending { it.updatedAt }

    override suspend fun delete(photoId: String) {
        records.remove(photoId)
    }
}
