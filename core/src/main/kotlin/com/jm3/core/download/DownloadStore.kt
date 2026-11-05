package com.jm3.core.download

/** 单页取数：index 从 1 起。阻塞或挂起实现均可，队列在 IO 上下文驱动。 */
fun interface PageFetcher {
    suspend fun fetch(photoId: String, index: Int): ByteArray
}

/** 已下载文件的本地存储；目录语义由实现方决定。 */
interface DownloadStore {
    fun exists(photoId: String, index: Int): Boolean

    /**
     * 写入单页字节；目标已存在时必须原子替换，且不许留下临时文件。
     *
     * 覆盖是被允许的语义，不许依赖平台 rename 行为（Windows 上改名到已存在目标会失败）。
     */
    fun write(photoId: String, index: Int, bytes: ByteArray)

    fun delete(photoId: String)

    /** 已落盘页号列表（升序）。 */
    fun files(photoId: String): List<Int>

    /** 读已下载页字节；不存在返回 null。 */
    fun read(photoId: String, index: Int): ByteArray?
}

/** 开跑前补齐章节元数据（total<=0 的任务必须解析）。 */
data class ResolvedChapter(
    val albumId: String,
    val title: String,
    val total: Int,
)

fun interface TaskResolver {
    suspend fun resolve(photoId: String): ResolvedChapter
}
