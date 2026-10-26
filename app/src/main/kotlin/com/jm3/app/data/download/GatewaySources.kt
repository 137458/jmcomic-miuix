package com.jm3.app.data.download

import com.jm3.core.Jm3Gateway
import com.jm3.core.download.PageFetcher
import com.jm3.core.download.ResolvedChapter
import com.jm3.core.download.TaskResolver
import com.jm3.core.model.PageImage
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线页取数：拉取 photo 页列表后按页号定位，逐页走 [Jm3Gateway.imageBytes]。
 * 会话内缓存 photoId -> 页列表，避免每次重试都重拉元数据。
 */
class GatewayPageFetcher(private val gateway: Jm3Gateway) : PageFetcher {

    private val pagesCache = ConcurrentHashMap<String, List<PageImage>>()

    suspend fun pages(photoId: String): List<PageImage> =
        pagesCache[photoId] ?: gateway.photo(photoId).images.also { pagesCache[photoId] = it }

    /** 页列表 JSON；仅当会话内已拉取过该 photo 时可序列化，供离线阅读缓存。 */
    fun pagesJsonOrNull(photoId: String): String? {
        val pages = pagesCache[photoId] ?: return null
        return runCatching { encodePhotoPages(photoId, pages) }.getOrNull()
    }

    override suspend fun fetch(photoId: String, index: Int): ByteArray {
        val image = pages(photoId).firstOrNull { it.index == index }
            ?: error("页号越界：photoId=$photoId index=$index")
        return gateway.imageBytes(image)
    }
}

/** 补齐章节元数据：photoId -> albumId/title/总页数。 */
class GatewayTaskResolver(private val gateway: Jm3Gateway) : TaskResolver {
    override suspend fun resolve(photoId: String): ResolvedChapter {
        val photo = gateway.photo(photoId)
        return ResolvedChapter(albumId = photo.albumId, title = photo.title, total = photo.images.size)
    }
}
