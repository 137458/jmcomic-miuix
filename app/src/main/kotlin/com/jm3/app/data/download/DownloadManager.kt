package com.jm3.app.data.download

import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.DownloadTask
import com.jm3.core.model.PhotoPages
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal val downloadJson = Json { ignoreUnknownKeys = true }

/** 页列表 <-> JSON（photoId 为 key，albumId/title 以记录表为准）。 */
internal fun encodePhotoPages(photoId: String, images: List<com.jm3.core.model.PageImage>): String =
    downloadJson.encodeToString(PhotoPages(photoId = photoId, albumId = "", title = "", images = images))

/** 反序列化离线页列表；损坏时返回 null 走在线回退。 */
internal fun decodePhotoPages(json: String): PhotoPages? = runCatching {
    downloadJson.decodeFromString(PhotoPages.serializer(), json)
}.getOrNull()

/** 本地书架记录 + 离线页列表 JSON。 */
data class DownloadRecordWithPages(
    val record: DownloadRecord,
    val pagesJson: String?,
)

/** 下载元数据存储：记录 + 离线阅读所需的页列表 JSON。 */
interface DownloadRecordStore {
    suspend fun upsert(record: DownloadRecord, pagesJson: String?)

    suspend fun all(): List<DownloadRecord>

    suspend fun find(photoId: String): DownloadRecordWithPages?

    suspend fun delete(photoId: String)
}

/**
 * 下载门面：启动队列、订阅进度落库、提供 UI 动作。
 */
class DownloadManager(
    val queue: DownloadQueue,
    private val fileStore: DownloadStore,
    private val recordStore: DownloadRecordStore,
    private val pageSource: GatewayPageFetcher,
    private val appScope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    /** 幂等启动：worker 池 + 进度持久化订阅。 */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch { queue.start(appScope) }
        appScope.launch {
            queue.updates.collect { tasks ->
                tasks.values.forEach { task ->
                    recordStore.upsert(
                        record = DownloadRecord(
                            photoId = task.photoId,
                            albumId = task.albumId,
                            title = task.title,
                            total = task.total,
                            donePages = task.index,
                            status = task.status,
                            updatedAt = 0L,
                        ),
                        pagesJson = pageSource.pagesJsonOrNull(task.photoId),
                    )
                }
            }
        }
    }

    /** 章节入队（total 未知，开跑前由 resolver 补齐）。 */
    fun enqueueChapter(photoId: String, albumId: String, title: String) {
        ensureStarted()
        queue.enqueue(DownloadTask(photoId = photoId, albumId = albumId, title = title, total = 0))
    }

    suspend fun records(): List<DownloadRecord> = recordStore.all()

    suspend fun find(photoId: String): DownloadRecordWithPages? = recordStore.find(photoId)

    fun pause(photoId: String) = queue.pause(photoId)

    fun resume(photoId: String) = queue.resume(photoId)

    /** 删除：取消任务 + 清文件 + 清记录。 */
    fun remove(photoId: String) {
        queue.cancel(photoId)
        fileStore.delete(photoId)
        appScope.launch { recordStore.delete(photoId) }
    }

    /** 清空全部下载：取消在途任务 + 删文件 + 删记录（文件与记录双清）。 */
    suspend fun clearAll() {
        val all = recordStore.all()
        all.forEach { queue.cancel(it.photoId) }
        all.forEach { record ->
            fileStore.delete(record.photoId)
            recordStore.delete(record.photoId)
        }
    }
}
