package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.CommentPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.enums.ForumMode
import io.github.jukomu.jmcomic.api.model.ForumQuery
import io.github.jukomu.jmcomic.api.model.SearchQuery
import io.github.jukomu.jmcomic.core.JmComic
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration

interface Jm3Gateway {
    /** 搜索。阻塞调用，调用方需自行切到 IO 线程。 */
    fun search(text: String, page: Int): SearchPage

    /** 分类筛选/榜单浏览（categories/filter 接口）。阻塞调用。 */
    fun browse(filter: BrowseFilter, page: Int): SearchPage

    /** 相册详情（含章节列表）。阻塞调用。 */
    fun album(albumId: String): AlbumDetail

    /** 相册评论区（只读，一页）。阻塞调用。 */
    fun comments(albumId: String, page: Int = 1): CommentPage

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
        return client.search(query).toPage()
    }

    override fun browse(filter: BrowseFilter, page: Int): SearchPage =
        client.getCategories(filter.toSearchQuery(page)).toPage()

    override fun album(albumId: String): AlbumDetail {
        val album = client.getAlbum(albumId)
        val cover = runCatching { client.getAlbumCoverUrl(albumId, "") }.getOrNull()
        return album.toAlbumDetail(coverUrl = cover)
    }

    override fun comments(albumId: String, page: Int): CommentPage {
        val query = ForumQuery.album(albumId).mode(ForumMode.ALL).page(page).build()
        return client.getComments(query).toCommentPage()
    }

    override fun photo(photoId: String): PhotoPages = client.getPhoto(photoId).toPhotoPages()

    override fun imageBytes(image: PageImage): ByteArray = client.fetchImageBytes(image.toJmImage())

    override fun coverUrl(albumId: String, size: String): String? =
        runCatching { client.getAlbumCoverUrl(albumId, size) }.getOrNull()

    /** 域名解析只做一次：借库的域名裁决取首个封面地址，解析出图片域名后全部走纯构造器，消除逐项调用。 */
    private fun io.github.jukomu.jmcomic.api.model.JmSearchPage.toPage(): SearchPage {
        val metas = content().orEmpty()
        val imageDomain = metas.firstOrNull()
            ?.let { runCatching { client.getAlbumCoverUrl(it.id(), "_3x4") }.getOrNull() }
            ?.let { it.removePrefix("https://").substringBefore("/") }
            .orEmpty()
        val items = metas.map { meta ->
            meta.toSearchResult(coverUrl = CoverUrls.build(meta.id(), imageDomain, "_3x4"))
        }
        return SearchPage(currentPage = currentPage(), totalPages = totalPages(), items = items)
    }
}
