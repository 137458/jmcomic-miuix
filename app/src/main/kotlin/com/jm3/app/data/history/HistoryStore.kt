package com.jm3.app.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.jm3.core.history.HistoryEntry
import com.jm3.core.history.HistoryRepository

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val photoId: String,
    val albumId: String,
    val title: String,
    val coverUrl: String?,
    val updatedAt: Long,
)

@Dao
interface HistoryDao {

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT :pageSize OFFSET :offset")
    suspend fun page(pageSize: Int, offset: Int): List<HistoryEntity>
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

/** Room 实现：写入打点由 clock 注入。 */
class RoomHistoryRepository(
    private val dao: HistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : HistoryRepository {

    override suspend fun upsert(entry: HistoryEntry) {
        dao.upsert(entry.toEntity(updatedAt = clock()))
    }

    override suspend fun page(page: Int, pageSize: Int): List<HistoryEntry> {
        if (page < 1) return emptyList()
        return dao.page(pageSize = pageSize, offset = (page - 1) * pageSize).map { it.toEntry() }
    }
}

private fun HistoryEntry.toEntity(updatedAt: Long) = HistoryEntity(
    photoId = photoId,
    albumId = albumId,
    title = title,
    coverUrl = coverUrl,
    updatedAt = updatedAt,
)

private fun HistoryEntity.toEntry() = HistoryEntry(
    photoId = photoId,
    albumId = albumId,
    title = title,
    coverUrl = coverUrl,
    updatedAt = updatedAt,
)
