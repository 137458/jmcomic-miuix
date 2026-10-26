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
