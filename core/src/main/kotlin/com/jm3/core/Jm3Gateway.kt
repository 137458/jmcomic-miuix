package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.model.SearchQuery
import io.github.jukomu.jmcomic.core.JmComic
import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration

interface Jm3Gateway {
    /** 搜索。阻塞调用，调用方需自行切到 IO 线程。 */
    fun search(text: String, page: Int): SearchPage

    /** 相册详情（含章节列表）。阻塞调用。 */
    fun album(albumId: String): AlbumDetail

    /** 章节图片清单。阻塞调用。 */
    fun photo(photoId: String): PhotoPages

    /** 章节图片二进制（已还原分割）。阻塞调用。 */
    fun imageBytes(image: PageImage): ByteArray

    /** 封面地址；域名未就绪等场景返回 null。 */
    fun coverUrl(albumId: String, size: String = ""): String?
}

class Jm3GatewayImpl(
    private val clientProvider: () -> JmApiClient = {
        @Suppress("DEPRECATION")
        JmComic.newApiClient(JmConfiguration.Builder().build())
    },
) : Jm3Gateway {

    private val client: JmApiClient by lazy(clientProvider)

    override fun search(text: String, page: Int): SearchPage {
        val query = SearchQuery.Builder().text(text).page(page).build()
        val result = client.search(query)
        val items = result.content().orEmpty().map { meta ->
            meta.toSearchResult(coverUrl = runCatching { client.getAlbumCoverUrl(meta.id(), "_3x4") }.getOrNull())
        }
        return SearchPage(currentPage = result.currentPage(), totalPages = result.totalPages(), items = items)
    }

    override fun album(albumId: String): AlbumDetail {
        val album = client.getAlbum(albumId)
        val cover = runCatching { client.getAlbumCoverUrl(albumId, "") }.getOrNull()
        return album.toAlbumDetail(coverUrl = cover)
    }

    override fun photo(photoId: String): PhotoPages = client.getPhoto(photoId).toPhotoPages()

    override fun imageBytes(image: PageImage): ByteArray {
        val jmImage = io.github.jukomu.jmcomic.api.model.JmImage(
            image.photoId,
            image.scrambleId,
            image.filename,
            image.url,
            "",
            image.index,
        )
        return client.fetchImageBytes(jmImage)
    }

    override fun coverUrl(albumId: String, size: String): String? =
        runCatching { client.getAlbumCoverUrl(albumId, size) }.getOrNull()
}
