package com.jm3.app.miuix.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jm3.core.download.DownloadLedgerEntry
import com.jm3.core.download.DownloadLedgerStore
import com.jm3.core.download.DownloadLedgerWrite
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.history.HistoryRepository

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["updatedAt"]),
    ],
)
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

    @Query("SELECT * FROM history WHERE albumId = :albumId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForAlbum(albumId: String): HistoryEntity?

    @Query("SELECT photoId FROM history WHERE albumId = :albumId")
    suspend fun photoIdsForAlbum(albumId: String): List<String>

    @Query("DELETE FROM history WHERE photoId = :photoId")
    suspend fun delete(photoId: String)

    @Query("DELETE FROM history WHERE albumId = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

/** 本地书架记录；pagesJson 缓存已完成章节的页列表，供离线阅读。 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["updatedAt"]),
    ],
)
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

    /**
     * 进度写：只列元数据列，未列出的 pagesJson 由 SQL 语义原样保留。
     *
     * 这条语句是「进度写不得抹掉离线页列表」的唯一实现手段：把 pagesJson 从进度路径上
     * 摘掉，而不是靠调用方记得传旧值。返回受影响行数，0 表示行不存在。
     */
    @Query(
        "UPDATE downloads SET albumId=:albumId, title=:title, total=:total, " +
            "donePages=:donePages, status=:status, updatedAt=:updatedAt WHERE photoId=:photoId",
    )
    suspend fun updateProgress(
        photoId: String,
        albumId: String,
        title: String,
        total: Int,
        donePages: Int,
        status: String,
        updatedAt: Long,
    ): Int

    /** 首次出现的任务补插一行；已存在时不动（进度由 updateProgress 负责）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: DownloadEntity): Long

    /** 原子进度写：先尝试只更新元数据列（保留 pagesJson），若行尚不存在再补插。 */
    @Transaction
    suspend fun upsertProgress(entry: DownloadEntity) {
        val updated = updateProgress(
            photoId = entry.photoId,
            albumId = entry.albumId,
            title = entry.title,
            total = entry.total,
            donePages = entry.donePages,
            status = entry.status,
            updatedAt = entry.updatedAt,
        )
        if (updated == 0) {
            insertIfAbsent(entry)
        }
    }

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

/** v3：为 history(albumId/updatedAt) 与 downloads(updatedAt) 补建索引；禁止破坏性迁移。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS [index_history_albumId] ON [history] ([albumId])")
        db.execSQL("CREATE INDEX IF NOT EXISTS [index_history_updatedAt] ON [history] ([updatedAt])")
        db.execSQL("CREATE INDEX IF NOT EXISTS [index_downloads_updatedAt] ON [downloads] ([updatedAt])")
    }
}

/** 当前未配置 room.schemaLocation，保持 exportSchema = false；改表结构须同步升版本并提供显式 Migration。 */
@Database(entities = [HistoryEntity::class, DownloadEntity::class], version = 3, exportSchema = false)
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

    override suspend fun latestForAlbum(albumId: String): HistoryEntry? =
        dao.latestForAlbum(albumId)?.toEntry()

    override suspend fun readPhotoIds(albumId: String): Set<String> =
        dao.photoIdsForAlbum(albumId).toSet()

    override suspend fun delete(photoId: String) {
        dao.delete(photoId)
    }

    override suspend fun deleteAlbum(albumId: String) {
        dao.deleteAlbum(albumId)
    }

    override suspend fun clear() {
        dao.clear()
    }
}

/** 本地书架的 Room 存储：实现 :core 的 [DownloadLedgerStore]，两种写形状分别对应两条语句。 */
class RoomDownloadLedgerStore(
    private val dao: DownloadDao,
) : DownloadLedgerStore {

    override suspend fun put(write: DownloadLedgerWrite) {
        val record = write.record
        when (write) {
            is DownloadLedgerWrite.Progress ->
                dao.upsertProgress(record.toEntity(updatedAt = record.updatedAt, pagesJson = null))

            is DownloadLedgerWrite.Completed ->
                dao.upsert(record.toEntity(updatedAt = record.updatedAt, pagesJson = write.pagesJson))
        }
    }

    override suspend fun all(): List<DownloadRecord> = dao.all().map { it.toRecord() }

    override suspend fun find(photoId: String): DownloadLedgerEntry? =
        dao.find(photoId)?.let { entity ->
            DownloadLedgerEntry(record = entity.toRecord(), pagesJson = entity.pagesJson)
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
