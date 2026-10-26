package com.jm3.app.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jm3.app.data.download.DownloadRecordStore
import com.jm3.app.data.download.DownloadRecordWithPages
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
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

/** 本地书架记录；pagesJson 缓存已完成章节的页列表，供离线阅读。 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val photoId: String,
    val albumId: String,
    val title: String,
    val total: Int,
    val donePages: Int,
    val status: String,
    val updatedAt: Long,
    val pagesJson: String?,
)

@Dao
interface DownloadDao {

    @Upsert
    suspend fun upsert(entry: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE photoId = :photoId")
    suspend fun find(photoId: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE photoId = :photoId")
    suspend fun delete(photoId: String)
}

/** v2：新增 downloads 表（本地书架）。v1 库不含该表，需显式建表；禁止破坏性迁移。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS [downloads] (" +
                "[photoId] TEXT NOT NULL, " +
                "[albumId] TEXT NOT NULL, " +
                "[title] TEXT NOT NULL, " +
                "[total] INTEGER NOT NULL, " +
                "[donePages] INTEGER NOT NULL, " +
                "[status] TEXT NOT NULL, " +
                "[updatedAt] INTEGER NOT NULL, " +
                "[pagesJson] TEXT, " +
                "PRIMARY KEY([photoId]))",
        )
    }
}

@Database(entities = [HistoryEntity::class, DownloadEntity::class], version = 2, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun downloadDao(): DownloadDao
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

/** 本地书架 Room 实现：实现 :core 的 DownloadRepository 与 app 的 DownloadRecordStore。 */
class RoomDownloadRepository(
    private val dao: DownloadDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : com.jm3.core.download.DownloadRepository, DownloadRecordStore {

    override suspend fun upsert(record: DownloadRecord) = upsert(record, null)

    override suspend fun upsert(record: DownloadRecord, pagesJson: String?) {
        dao.upsert(record.toEntity(updatedAt = clock(), pagesJson = pagesJson))
    }

    override suspend fun all(): List<DownloadRecord> = dao.all().map { it.toRecord() }

    override suspend fun find(photoId: String): DownloadRecordWithPages? =
        dao.find(photoId)?.let { entity ->
            DownloadRecordWithPages(record = entity.toRecord(), pagesJson = entity.pagesJson)
        }

    override suspend fun delete(photoId: String) {
        dao.delete(photoId)
    }
}

private fun DownloadRecord.toEntity(updatedAt: Long, pagesJson: String?) = DownloadEntity(
    photoId = photoId,
    albumId = albumId,
    title = title,
    total = total,
    donePages = donePages,
    status = status.name,
    updatedAt = updatedAt,
    pagesJson = pagesJson,
)

private fun DownloadEntity.toRecord() = DownloadRecord(
    photoId = photoId,
    albumId = albumId,
    title = title,
    total = total,
    donePages = donePages,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    updatedAt = updatedAt,
)

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
