package com.jm3.core.history

/** 单条阅读历史。 */
data class HistoryEntry(
    val photoId: String,
    val albumId: String,
    val title: String,
    val coverUrl: String?,
    val updatedAt: Long,
)

/** 阅读历史存储：按 photoId 去重，按更新时间倒序分页。 */
interface HistoryRepository {
    /** 新增或更新；同 photoId 覆盖并刷新更新时间。 */
    suspend fun upsert(entry: HistoryEntry)

    /** 页码从 1 起；非法页码或越界返回空列表。 */
    suspend fun page(page: Int, pageSize: Int = 20): List<HistoryEntry>

    /** 某作品最近一次阅读记录；无记录返回 null。 */
    suspend fun latestForAlbum(albumId: String): HistoryEntry?

    /** 某作品已读章节 id 集合。 */
    suspend fun readPhotoIds(albumId: String): Set<String>

    /** 清空全部历史。 */
    suspend fun clear()
}

/** 纯内存实现，供测试与后续替换。 */
class InMemoryHistoryRepository(private val clock: () -> Long = System::currentTimeMillis) : HistoryRepository {

    private val entries = LinkedHashMap<String, HistoryEntry>()

    override suspend fun upsert(entry: HistoryEntry) {
        entries[entry.photoId] = entry.copy(updatedAt = clock())
    }

    override suspend fun page(page: Int, pageSize: Int): List<HistoryEntry> {
        if (page < 1) return emptyList()
        val all = entries.values.sortedByDescending { it.updatedAt }
        val from = (page - 1) * pageSize
        if (from >= all.size) return emptyList()
        return all.subList(from, minOf(from + pageSize, all.size))
    }

    override suspend fun latestForAlbum(albumId: String): HistoryEntry? =
        entries.values
            .filter { it.albumId == albumId }
            .maxByOrNull { it.updatedAt }

    override suspend fun readPhotoIds(albumId: String): Set<String> =
        entries.values
            .filter { it.albumId == albumId }
            .mapTo(HashSet()) { it.photoId }

    override suspend fun clear() {
        entries.clear()
    }
}
