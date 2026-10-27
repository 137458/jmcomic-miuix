package com.jm3.app.data.download

import com.jm3.core.Jm3Gateway
import com.jm3.core.download.PageFetcher
import com.jm3.core.download.PageNumbers
import com.jm3.core.download.ResolvedChapter
import com.jm3.core.download.TaskResolver
import com.jm3.core.model.PageImage
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线页取数：拉取 photo 页列表后按页号定位，逐页走 [Jm3Gateway.imageBytes]。
 * 会话内缓存 photoId -> 页列表，避免每次重试都重拉元数据。
 * 通过 provider 取网关：线路切换重建网关后自动跟随，不持旧实例。
 */
class GatewayPageFetcher(private val gatewayProvider: () -> Jm3Gateway) : PageFetcher {

    private val pagesCache = ConcurrentHashMap<String, List<PageImage>>()

    suspend fun pages(photoId: String): List<PageImage> =
        pagesCache[photoId] ?: gatewayProvider().photo(photoId).images.also { pagesCache[photoId] = it }

    /** 页列表 JSON；仅当会话内已拉取过该 photo 时可序列化，供离线阅读缓存。 */
    fun pagesJsonOrNull(photoId: String): String? {
        val pages = pagesCache[photoId] ?: return null
        return runCatching { encodePhotoPages(photoId, pages) }.getOrNull()
    }

    override suspend fun fetch(photoId: String, index: Int): ByteArray {
        // index 为 1 起页号，页列表下标 0 起，统一经 PageNumbers 换算。
        val image = pages(photoId).firstOrNull { it.index == PageNumbers.imageIndex(index) }
            ?: error("页号越界：photoId=$photoId 页号=$index")
        return gatewayProvider().imageBytes(image)
    }
}

/** 补齐章节元数据：photoId -> albumId/title/总页数。 */
class GatewayTaskResolver(private val gatewayProvider: () -> Jm3Gateway) : TaskResolver {
    override suspend fun resolve(photoId: String): ResolvedChapter {
        val photo = gatewayProvider().photo(photoId)
        return ResolvedChapter(albumId = photo.albumId, title = photo.title, total = photo.images.size)
    }
}
