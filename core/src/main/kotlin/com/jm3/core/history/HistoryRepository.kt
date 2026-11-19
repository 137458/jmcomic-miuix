package com.jm3.core.history

/** 单条阅读历史。 */
data class HistoryEntry(
    val photoId: String,
    val albumId: String,
    val title: String,
    val coverUrl: String?,
    val updatedAt: Long,
)

/** 续页游标：上一页最后一条的（更新时间, photoId）。photoId 是同一时间戳下的稳定次序键。 */
data class HistoryCursor(
    val updatedAt: Long,
    val photoId: String,
)

/** 阅读历史存储：按 photoId 去重，按更新时间倒序分页。 */
interface HistoryRepository {
    /** 新增或更新；同 photoId 覆盖并刷新更新时间。 */
    suspend fun upsert(entry: HistoryEntry)

    /** 页码从 1 起；非法页码或越界返回空列表。用于用户显式跳页，不用于往下续页。 */
    suspend fun page(page: Int, pageSize: Int = 20): List<HistoryEntry>

    /**
     * 续页：取 [cursor] 之后的一页，cursor 为 null 时等于首页。
     *
     * 「加载更多」必须走游标而不是页码：页码换算成 OFFSET 后，只要列表上方发生了插入或删除
     * （读者自己读的章节会被顶到最前），同一个页边界就会整体错位，续页要么重复已读条目、
     * 要么漏掉一条。
     */
    suspend fun pageAfter(cursor: HistoryCursor?, pageSize: Int = 20): List<HistoryEntry>

    /** 某作品最近一次阅读记录；无记录返回 null。 */
    suspend fun latestForAlbum(albumId: String): HistoryEntry?

    /** 某作品已读章节 id 集合。 */
    suspend fun readPhotoIds(albumId: String): Set<String>

    /** 删除单条章节阅读记录，默认不实现（内存实现覆盖）。 */
    suspend fun delete(photoId: String) {}

    /** 删除某作品的全部阅读记录。 */
    suspend fun deleteAlbum(albumId: String) {}

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
        if (page < 1 || pageSize <= 0) return emptyList()
        val all = sortedEntries()
        val fromLong = (page.toLong() - 1L) * pageSize.toLong()
        if (fromLong >= all.size.toLong()) return emptyList()
        val from = fromLong.toInt()
        val to = minOf(fromLong + pageSize.toLong(), all.size.toLong()).toInt()
        return all.subList(from, to)
    }

    override suspend fun pageAfter(cursor: HistoryCursor?, pageSize: Int): List<HistoryEntry> {
        if (pageSize <= 0) return emptyList()
        val ordered = sortedEntries()
        if (cursor == null) return ordered.take(pageSize)
        val start = ordered.indexOfFirst { it.isAfter(cursor) }
        if (start < 0) return emptyList()
        return ordered.subList(start, minOf(start + pageSize, ordered.size))
    }

    private fun sortedEntries(): List<HistoryEntry> =
        entries.values.sortedWith(compareByDescending<HistoryEntry> { it.updatedAt }.thenBy { it.photoId })

    override suspend fun latestForAlbum(albumId: String): HistoryEntry? =
        entries.values
            .filter { it.albumId == albumId }
            .maxByOrNull { it.updatedAt }

    override suspend fun readPhotoIds(albumId: String): Set<String> =
        entries.values
            .filter { it.albumId == albumId }
            .mapTo(HashSet()) { it.photoId }

    override suspend fun delete(photoId: String) {
        entries.remove(photoId)
    }

    override suspend fun deleteAlbum(albumId: String) {
        entries.values.removeAll { it.albumId == albumId }
    }

    override suspend fun clear() {
        entries.clear()
    }
}

/** 这一条是否排在游标之后：更新时间更早，或同一时间戳但 photoId 更大（倒序 + photoId 正序）。 */
private fun HistoryEntry.isAfter(cursor: HistoryCursor): Boolean =
    updatedAt < cursor.updatedAt || (updatedAt == cursor.updatedAt && photoId > cursor.photoId)
