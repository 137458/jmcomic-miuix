package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.CommentPage
import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.CreatorAuthorWorks
import com.jm3.core.model.CreatorPage
import com.jm3.core.model.CreatorWork
import com.jm3.core.model.CreatorWorkPage
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.model.ForumScope
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.NotificationPage
import com.jm3.core.model.NovelChapter
import com.jm3.core.model.NovelDetail
import com.jm3.core.model.NovelPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.model.TagFavorite
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import com.jm3.core.model.TrackingPage
import com.jm3.core.model.UserProfile
import com.jm3.core.model.WeeklyPicks
import com.jm3.core.model.WeeklyPicksDetail
import io.github.jukomu.jmcomic.api.enums.ForumMode
import io.github.jukomu.jmcomic.api.model.ForumQuery
import io.github.jukomu.jmcomic.api.model.JmFavoritePage
import io.github.jukomu.jmcomic.core.JmComic
import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration

interface Jm3Gateway {
    /** 搜索。scope 对应原版搜索类型（站内/作品/作者/标签/登场人物），time 由服务端过滤时间范围。阻塞调用。 */
    fun search(
        text: String,
        page: Int,
        order: BrowseOrder = BrowseOrder.LATEST,
        scope: SearchScope = SearchScope.SITE,
        time: BrowseTime = BrowseTime.ALL,
    ): SearchPage

    /** 最新上架列表。阻塞调用。 */
    fun latest(page: Int): SearchPage

    /** 热门搜索标签；接口不可用时返回空表。阻塞调用。 */
    fun hotTags(): List<String>

    /** 随机推荐。阻塞调用。 */
    fun randomRecommend(): List<SearchResult>

    /** 分类筛选/榜单浏览（categories/filter 接口）。阻塞调用。 */
    fun browse(filter: BrowseFilter, page: Int): SearchPage

    /** 连载更新列表（原版「每周连载更新」域）。阻塞调用。 */
    fun serialization(page: Int): SearchPage

    /** 分类树（一级分类 + 子分类 + 标签区块）。阻塞调用。 */
    fun categoriesList(): CategoryTree

    /** 相册详情（含章节列表）。阻塞调用。 */
    fun album(albumId: String): AlbumDetail

    /** 相册评论区（只读，一页）。阻塞调用。 */
    fun comments(albumId: String, page: Int = 1): CommentPage

    /** 发表本子评论。阻塞调用。 */
    fun postComment(albumId: String, text: String)

    /** 回复某条评论。阻塞调用。 */
    fun replyComment(albumId: String, text: String, parentCommentId: String)

    /** 论坛列表：全站/漫画评论/闲聊三档。阻塞调用。 */
    fun forum(scope: ForumScope, page: Int = 1): CommentPage

    /** 切换本子点赞（平台不支持取消）。阻塞调用。 */
    fun toggleLike(albumId: String)

    /** 连载追踪状态。阻塞调用。 */
    fun isTracking(albumId: String): Boolean

    /** 切换连载追踪。阻塞调用。 */
    fun setTracking(albumId: String)

    /** 章节图片清单。阻塞调用。 */
    fun photo(photoId: String): PhotoPages

    /** 章节图片二进制（已还原分割）。阻塞调用。 */
    fun imageBytes(image: PageImage): ByteArray

    /** 封面地址；域名未就绪等场景返回 null。 */
    fun coverUrl(albumId: String, size: String = "_3x4"): String?

    /** 登录并返回会话（含 AVS Cookie 快照）。阻塞调用。 */
    fun login(username: String, password: String): Jm3Session

    /** 登出并清空库内会话。阻塞调用。 */
    fun logout()

    /** 恢复已持久化的会话：把 Cookie 注回库内 CookieJar。阻塞调用。 */
    fun restoreSession(session: Jm3Session)

    /** 收藏列表（folderId=0 全部收藏）。阻塞调用。 */
    fun favorites(page: Int): FavoritePage

    /** 指定收藏夹的收藏列表页。阻塞调用。 */
    fun favorites(folderId: Int, page: Int): FavoritePage

    /** 签到状态（含记录扁平统计）。阻塞调用。 */
    fun dailyCheckInStatus(userId: String): DailyCheckInStatus

    /** 执行当日签到。阻塞调用。 */
    fun dailyCheckIn(userId: String, dailyId: Int)

    /** 每周必看期数列表。阻塞调用。 */
    fun weeklyPicks(): WeeklyPicks

    /** 每周必看某一期的作品集合（接口一次返回全部）。阻塞调用。 */
    fun weeklyPicksDetail(categoryId: String): WeeklyPicksDetail

    /** 站内通知/信箱列表。阻塞调用。 */
    fun notifications(): NotificationPage

    /** 标记单条通知已读/未读。阻塞调用。 */
    fun markNotification(id: String, read: Boolean)

    /** 连载追踪列表。阻塞调用。 */
    fun trackingList(page: Int): TrackingPage

    /** 成就/任务列表（type=称号/勋章，filter=我的/全部）。阻塞调用。 */
    fun tasks(type: TaskType, filter: TaskFilter): TaskPage

    /** 图片域名（媒体与 API 同域），用于拼接勋章图等相对路径；未就绪时返回空串。阻塞调用。 */
    fun imageHost(): String

    /** 个人资料。阻塞调用。 */
    fun userProfile(uid: String): UserProfile

    /** 编辑个人资料并返回更新后的资料。阻塞调用。 */
    fun editUserProfile(uid: String, fields: Map<String, String>): UserProfile

    /** 服务端观看记录（一页）。阻塞调用。 */
    fun watchHistory(page: Int): List<SearchResult>

    /** 删除一条服务端观看记录。阻塞调用。 */
    fun deleteWatchHistory(id: String)

    /** 收藏标签列表。阻塞调用。 */
    fun tagsFavorite(): List<TagFavorite>

    /** 增加收藏标签。阻塞调用。 */
    fun addFavoriteTags(tags: List<String>)

    /** 移除收藏标签。阻塞调用。 */
    fun removeFavoriteTags(tags: List<String>)

    /** 收藏夹管理（新建/重命名/移动/删除）。阻塞调用。 */
    fun manageFavoriteFolder(
        action: FavoriteFolderAction,
        folderId: String = "",
        name: String = "",
        albumId: String = "",
    ): FolderOperation

    /** 当前实际使用的 API 域名；域名未就绪时为 null。 */
    fun usedDomain(): String?

    /** 各域名延迟（毫秒）；空表表示尚未探测。 */
    fun domainLatency(): Map<String, Int>

    /** 重新探测全部域名。阻塞调用。 */
    fun reprobeDomains()

    /** 释放底层客户端资源（退出或切换线路时调用）。 */
    fun close()

    /** 切换收藏状态（已收藏则取消）；folderId 指定加入的收藏夹，默认「全部」。阻塞调用。 */
    fun toggleFavorite(albumId: String, folderId: String = "0")

    /** 小说列表；order 为原版排序值（空串=全部，mr=最新，mv=最多点阅，mp=最多章节，tf=最多爱心）。阻塞调用。 */
    fun novelList(order: String, page: Int = 1): NovelPage

    /** 小说关键词搜索。阻塞调用。 */
    fun searchNovels(query: String): NovelPage

    /** 小说详情（含章节列表与相关作品）。阻塞调用。 */
    fun novelDetail(novelId: String): NovelDetail

    /** 小说章节正文；lang 为 tw/cn。阻塞调用。 */
    fun novelChapter(chapterId: String, lang: String = "tw"): NovelChapter

    /** 小说点赞（平台不支持取消）。阻塞调用。 */
    fun toggleNovelLike(novelId: String)

    /** 切换小说收藏（已收藏则取消）。阻塞调用。 */
    fun toggleNovelFavorite(novelId: String)

    /** 小说评论区（整部作品）。阻塞调用。 */
    fun novelComments(novelId: String, page: Int = 1): CommentPage

    /** 发表小说评论；chapterId 为空表示评论整部作品。阻塞调用。 */
    fun postNovelComment(novelId: String, text: String, chapterId: String = "")

    /** 回复小说评论。阻塞调用。 */
    fun replyNovelComment(
        novelId: String,
        text: String,
        parentCommentId: String,
        chapterId: String = "",
    )

    /** 创作者列表（原版书库「创作者」Tab）。阻塞调用。 */
    fun creatorAuthors(page: Int = 1, query: String = ""): CreatorPage

    /** 创作者作品列表；lang/source 为空表示全部。阻塞调用。 */
    fun creatorWorks(
        page: Int = 1,
        query: String = "",
        lang: String = "",
        source: String = "",
    ): CreatorWorkPage

    /** 作者主页（头像/背景/赞助入口/作品集合）。阻塞调用。 */
    fun creatorAuthorWorks(
        creatorId: String,
        lang: String = "",
        source: String = "",
        page: Int = 1,
    ): CreatorAuthorWorks

    /** 作品详情（描述 + 整页图片 + 相关作品）。阻塞调用。 */
    fun creatorWork(workId: String): CreatorWork
}

class Jm3GatewayImpl internal constructor(
    private val clientProvider: () -> JmApiClient,
    private val domainControlsProvider: () -> DomainControls,
) : Jm3Gateway {

    /** 生产装配：域名能力取自库客户端（JmApiClient 实际派生自 AbstractJmClient，v1.1.12 已核实）。 */
    constructor(
        clientProvider: () -> JmApiClient = {
            @Suppress("DEPRECATION")
            JmComic.newApiClient(JmConfiguration.Builder().build())
        },
    ) : this(clientProvider, {
        val client = clientProvider()
        LibraryDomainControls(
            client as? AbstractJmClient
                ?: error("jmcomic v1.1.12 的 JmApiClient 必须派生自 AbstractJmClient，域名能力不可用"),
        )
    })

    private val client: JmApiClient by lazy(clientProvider)

    private val domainControls: DomainControls by lazy(domainControlsProvider)

    override fun search(
        text: String,
        page: Int,
        order: BrowseOrder,
        scope: SearchScope,
        time: BrowseTime,
    ): SearchPage = client.search(searchQuery(text, page, scope.toMainTag(), order, time)).toPage()

    override fun latest(page: Int): SearchPage = client.getLatest(page).toPage()

    override fun hotTags(): List<String> = runCatching { client.getHotTags() }.getOrDefault(emptyList())

    override fun randomRecommend(): List<SearchResult> {
        val metas = client.getRandomRecommend()
        val domain = imageDomain(metas.firstOrNull()?.id().orEmpty())
        return metas.map { it.toSearchResult(coverUrl = CoverUrls.build(it.id(), domain, "_3x4")) }
    }

    override fun browse(filter: BrowseFilter, page: Int): SearchPage =
        client.getCategories(filter.toSearchQuery(page)).toPage()

    override fun serialization(page: Int): SearchPage = client.getSerialization(page).toPage()

    override fun categoriesList(): CategoryTree = client.getCategoriesList().toCategoryTree()

    override fun album(albumId: String): AlbumDetail {
        val album = client.getAlbum(albumId)
        val domain = imageDomain(albumId)
        // 详情页与列表保持完全一致的 _3x4 规格，保证封面显示内容一致且 100% 命中图片缓存
        val cover = CoverUrls.build(albumId, domain, "_3x4")
        return album.toAlbumDetail(
            coverUrl = cover,
            relatedCoverUrl = { meta -> CoverUrls.build(meta.id(), domain, "_3x4") },
        )
    }

    override fun comments(albumId: String, page: Int): CommentPage {
        val query = ForumQuery.album(albumId).mode(ForumMode.ALL).page(page).build()
        return client.getComments(query).toCommentPage(novelImageHost())
    }

    override fun postComment(albumId: String, text: String) {
        client.postComment(albumId, text)
    }

    override fun replyComment(albumId: String, text: String, parentCommentId: String) {
        client.replyToComment(albumId, text, parentCommentId)
    }

    override fun forum(scope: ForumScope, page: Int): CommentPage {
        val mode = when (scope) {
            ForumScope.ALL -> ForumMode.ALL
            ForumScope.MANHUA -> ForumMode.MANHUA
            ForumScope.CHAT -> ForumMode.CHAT
        }
        // 库的 ForumQuery 强制要求实体 ID（entityId 为空直接抛异常），而原版全站论坛请求不带 aid。
        // 以 "0" 作为无实体占位，服务端按 mode 返回；若接口不支持则表现为空列表，由页面空态兜底。
        val query = ForumQuery.album("0").mode(mode).page(page).build()
        return client.getComments(query).toCommentPage(novelImageHost())
    }

    override fun toggleLike(albumId: String) = client.toggleAlbumLike(albumId)

    override fun isTracking(albumId: String): Boolean = client.getAlbumSertracking(albumId)

    override fun setTracking(albumId: String) = client.setAlbumSertracking(albumId)

    override fun photo(photoId: String): PhotoPages = client.getPhoto(photoId).toPhotoPages()

    override fun imageBytes(image: PageImage): ByteArray = client.fetchImageBytes(image.toJmImage())

    override fun coverUrl(albumId: String, size: String): String? =
        runCatching { client.getAlbumCoverUrl(albumId, size) }.getOrNull()

    override fun login(username: String, password: String): Jm3Session {
        val info = client.login(username, password)
        return Jm3Session(user = info.toUser(), cookies = client.cookies.map { it.toSnapshot() })
    }

    override fun logout() = client.logout()

    override fun restoreSession(session: Jm3Session) {
        // 线路由库在每次启动后重新裁决，登录域未必再被选中：会话 Cookie 需覆盖当前全部候选域名。
        val domains = domainControls.candidateDomains()
        client.setCookies(session.cookies.broadcastTo(domains).map { it.toOkhttpCookie() })
    }

    override fun favorites(page: Int): FavoritePage = favorites(0, page)

    override fun favorites(folderId: Int, page: Int): FavoritePage =
        client.getFavorites(favoritesQuery(folderId, page)).toFavoritePageWithCovers()

    override fun dailyCheckInStatus(userId: String): DailyCheckInStatus =
        client.getDailyCheckInStatus(userId).toDailyCheckInStatus()

    override fun dailyCheckIn(userId: String, dailyId: Int) = client.doDailyCheckin(userId, dailyId.toString())

    override fun weeklyPicks(): WeeklyPicks = client.getWeeklyPicksList().toWeeklyPicks()

    override fun weeklyPicksDetail(categoryId: String): WeeklyPicksDetail {
        val detail = client.getWeeklyPicksDetail(categoryId)
        val domain = imageDomain(detail.list().orEmpty().firstOrNull()?.id().orEmpty())
        return detail.toWeeklyPicksDetail { meta -> CoverUrls.build(meta.id(), domain, "_3x4") }
    }

    override fun notifications(): NotificationPage = client.getNotifications().toNotificationPage()

    override fun markNotification(id: String, read: Boolean) =
        client.markNotification(id, if (read) 1 else 0)

    override fun trackingList(page: Int): TrackingPage = client.getAlbumTrackingList(page).toTrackingPage()

    override fun tasks(type: TaskType, filter: TaskFilter): TaskPage =
        client.getTasks(type.apiValue, filter.apiValue).toTaskPage()

    override fun imageHost(): String = novelImageHost()

    override fun userProfile(uid: String): UserProfile = client.getUserProfile(uid).toUserProfile()

    override fun editUserProfile(uid: String, fields: Map<String, String>): UserProfile =
        client.editUserProfile(uid, fields).toUserProfile()

    override fun watchHistory(page: Int): List<SearchResult> {
        val metas = client.getWatchHistory(page).orEmpty()
        val domain = imageDomain(metas.firstOrNull()?.id().orEmpty())
        return metas.map { it.toSearchResult(coverUrl = CoverUrls.build(it.id(), domain, "_3x4")) }
    }

    override fun deleteWatchHistory(id: String) = client.deleteWatchHistory(id)

    override fun tagsFavorite(): List<TagFavorite> =
        client.getTagsFavorite().orEmpty().map { it.toTagFavorite() }

    override fun addFavoriteTags(tags: List<String>) = client.addFavoriteTags(tags)

    override fun removeFavoriteTags(tags: List<String>) = client.removeFavoriteTags(tags)

    override fun manageFavoriteFolder(
        action: FavoriteFolderAction,
        folderId: String,
        name: String,
        albumId: String,
    ): FolderOperation = client.manageFavoriteFolder(
        action.toFavoriteFolderType(),
        folderId,
        name,
        albumId,
    ).toFolderOperation()

    override fun usedDomain(): String? = domainControls.usedDomain()?.takeIf { it.isNotBlank() }

    override fun domainLatency(): Map<String, Int> = domainControls.domainLatency()

    override fun reprobeDomains() = domainControls.reprobeDomains()

    override fun close() = domainControls.close()

    override fun toggleFavorite(albumId: String, folderId: String) =
        client.toggleAlbumFavorite(albumId, folderId)

    override fun novelList(order: String, page: Int): NovelPage =
        client.getNovelList(order, page).toNovelPage(novelImageHost())

    override fun searchNovels(query: String): NovelPage =
        client.searchNovels(query).toNovelPage(novelImageHost())

    override fun novelDetail(novelId: String): NovelDetail =
        client.getNovelDetail(novelId).toNovelDetail(novelImageHost())

    override fun novelChapter(chapterId: String, lang: String): NovelChapter =
        client.getNovelChapter(chapterId, lang).toNovelChapter()

    override fun toggleNovelLike(novelId: String) = client.toggleNovelLike(novelId)

    override fun toggleNovelFavorite(novelId: String) = client.toggleNovelFavorite(novelId)

    override fun novelComments(novelId: String, page: Int): CommentPage {
        val query = ForumQuery.novel(novelId).mode(ForumMode.ALL).page(page).build()
        return client.getComments(query).toCommentPage(novelImageHost())
    }

    override fun postNovelComment(novelId: String, text: String, chapterId: String) {
        client.postNovelComment(novelId, text, chapterId)
    }

    override fun replyNovelComment(
        novelId: String,
        text: String,
        parentCommentId: String,
        chapterId: String,
    ) {
        client.replyToNovelComment(novelId, text, parentCommentId, chapterId)
    }

    override fun creatorAuthors(page: Int, query: String): CreatorPage =
        client.getCreatorAuthors(page, query).toCreatorPage(novelImageHost())

    override fun creatorWorks(page: Int, query: String, lang: String, source: String): CreatorWorkPage =
        client.getCreatorWorks(page, query, lang, source).toCreatorWorkPage(novelImageHost())

    override fun creatorAuthorWorks(
        creatorId: String,
        lang: String,
        source: String,
        page: Int,
    ): CreatorAuthorWorks =
        client.getCreatorAuthorWorks(creatorId, lang, source, page)
            .toCreatorAuthorWorks(novelImageHost())

    override fun creatorWork(workId: String): CreatorWork {
        // 弹窗信息（标题/作者/相关作品）失败不阻塞整页正文
        val info = runCatching { client.getCreatorWorkInfo(workId) }.getOrNull()
        return toCreatorWork(info, client.getCreatorWorkDetail(workId), novelImageHost())
    }

    /** 封面域名取库的线路裁决（库把封面挂到 bestDomain）：登录域在自动线路下恒为空，不能作域名来源。 */
    private fun novelImageHost(): String = domainControls.coverHost().orEmpty()

    /** 域名解析只做一次：借库的域名裁决取首个封面地址，解析出图片域名后全部走纯构造器，消除逐项调用。 */
    private fun io.github.jukomu.jmcomic.api.model.JmSearchPage.toPage(): SearchPage {
        val metas = content().orEmpty()
        val domain = imageDomain(metas.firstOrNull()?.id().orEmpty())
        return SearchPage(
            currentPage = currentPage(),
            totalPages = totalPages(),
            totalItems = totalItems(),
            items = metas.map { it.toSearchResult(coverUrl = CoverUrls.build(it.id(), domain, "_3x4")) },
        )
    }

    /** 图片域名裁决：借首项封面地址取 host，失败返回空串（封面降级为 null）。 */
    private fun imageDomain(albumId: String): String {
        if (albumId.isBlank()) return ""
        return runCatching { client.getAlbumCoverUrl(albumId, "_3x4") }.getOrNull()
            ?.removePrefix("https://")
            ?.substringBefore("/")
            .orEmpty()
    }

    /** 图片域名每页只解析一次，随后全部走纯构造器，避免逐项请求封面。 */
    private fun JmFavoritePage.toFavoritePageWithCovers(): FavoritePage {
        val imageDomain = imageDomain(content.orEmpty().firstOrNull()?.id().orEmpty())
        return toFavoritePage { meta -> CoverUrls.build(meta.id(), imageDomain, "_3x4") }
    }

    private fun okhttp3.Cookie.toSnapshot(): CookieSnapshot =
        CookieSnapshot(name = name, value = value, domain = domain, path = path)

    private fun CookieSnapshot.toOkhttpCookie(): okhttp3.Cookie =
        okhttp3.Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path.ifBlank { "/" })
            .build()
}

/**
 * 域名裁决与资源释放能力：对应库 [AbstractJmClient] 的公开方法。
 * JmApiClient 是 final class，单测无法伪造，抽成接口注入以便验证转发。
 */
internal interface DomainControls {
    fun usedDomain(): String?

    /** 当前线路候选域名；库初始化未完成时会阻塞到探活结束，调用方须在 IO 线程。 */
    fun candidateDomains(): Set<String>

    /** 封面域名：与库的封面地址构造共用同一裁决；未就绪时为 null。 */
    fun coverHost(): String?

    fun domainLatency(): Map<String, Int>

    fun reprobeDomains()

    fun close()
}

/** 取封面域名裁决用的占位本子号：只参与字符串拼接，不产生请求。 */
private const val COVER_PROBE_ALBUM_ID = "1"

/** [DomainControls] 的生产实现：直接委托库客户端。 */
private class LibraryDomainControls(private val client: AbstractJmClient) : DomainControls {

    override fun usedDomain(): String? = client.usedDomain

    /** 候选域名取库的线路表；初始化未完成时会阻塞到探活结束，调用方须在 IO 线程。 */
    override fun candidateDomains(): Set<String> = runCatching {
        client.domainStates.keys.filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())

    override fun coverHost(): String? = runCatching {
        // 封面地址是纯字符串拼接，不发请求；本子号只作占位，只为取库的域名裁决结果。
        client.getAlbumCoverUrl(COVER_PROBE_ALBUM_ID, "_3x4")
    }.getOrNull()
        ?.removePrefix("https://")
        ?.substringBefore("/")
        ?.takeIf { it.isNotBlank() }

    override fun domainLatency(): Map<String, Int> = client.domainLatency.orEmpty()

    override fun reprobeDomains() = client.reprobeDomains()

    override fun close() = client.close()
}
