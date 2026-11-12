package com.jm3.app.miuix.data

import com.jm3.core.Jm3Gateway
import com.jm3.core.SavedCredentials
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

/**
 * 手写的 [Jm3Gateway] 假实现：只给线路生命周期用例用，除 [close] 外的调用一律失败。
 *
 * 为什么手写 63 个方法而不是反射代理：代理按方法名字符串分派，接口改名、加参数在编译期毫无反应，
 * 只在运行到那条路径时才炸（同 [com.jm3.core.SessionAuth] 的注释，以及 :core 测试里 FakeSessionAuth 的方针）。
 * 手写版本由编译器盯着：Jm3Gateway 增删方法时这里直接编译不过，强制同步。
 *
 * 代价是这块样板随接口增长——真正的解法是让被测方只依赖它实际用到的那一面（见 PhotoSource 的抽取理由），
 * 该深化不在本次测试改动范围内。
 */
internal class FakeGateway(
    /** 网关被关闭时的回调：线路切换用例靠它记录关闭顺序。 */
    private val onClose: () -> Unit = {},
) : Jm3Gateway {

    override fun close() {
        onClose()
    }

    /** 除 [close] 外的方法都从这里失败：用例误调即报错，不会悄悄返回一个假数据把断言带偏。 */
    private fun unexpected(method: String): Nothing =
        error("FakeGateway 未配置 $method：该用例只验证线路切换生命周期，网关业务方法不应被调用")

    override fun login(username: String, password: String): Jm3Session = unexpected("login")

    override fun logout() = unexpected("logout")

    override fun restoreSession(session: Jm3Session) = unexpected("restoreSession")

    override fun primeCredentials(credentials: SavedCredentials) = unexpected("primeCredentials")

    override fun forgetCredentials() = unexpected("forgetCredentials")

    override fun currentCookies(): List<CookieSnapshot> = unexpected("currentCookies")

    override fun photo(photoId: String): PhotoPages = unexpected("photo")

    override fun imageBytes(image: PageImage): ByteArray = unexpected("imageBytes")

    override fun search(
        text: String,
        page: Int,
        order: BrowseOrder,
        scope: SearchScope,
        time: BrowseTime,
    ): SearchPage = unexpected("search")

    override fun latest(page: Int): SearchPage = unexpected("latest")

    override fun hotTags(): List<String> = unexpected("hotTags")

    override fun randomRecommend(): List<SearchResult> = unexpected("randomRecommend")

    override fun browse(filter: BrowseFilter, page: Int): SearchPage = unexpected("browse")

    override fun serialization(page: Int): SearchPage = unexpected("serialization")

    override fun categoriesList(): CategoryTree = unexpected("categoriesList")

    override fun album(albumId: String): AlbumDetail = unexpected("album")

    override fun comments(albumId: String, page: Int): CommentPage = unexpected("comments")

    override fun postComment(albumId: String, text: String) = unexpected("postComment")

    override fun replyComment(albumId: String, text: String, parentCommentId: String) = unexpected("replyComment")

    override fun forum(scope: ForumScope, page: Int): CommentPage = unexpected("forum")

    override fun toggleLike(albumId: String) = unexpected("toggleLike")

    override fun isTracking(albumId: String): Boolean = unexpected("isTracking")

    override fun setTracking(albumId: String) = unexpected("setTracking")

    override fun coverUrl(albumId: String, size: String): String? = unexpected("coverUrl")

    override fun favorites(page: Int): FavoritePage = unexpected("favorites")

    override fun favorites(folderId: Int, page: Int): FavoritePage = unexpected("favorites")

    override fun dailyCheckInStatus(userId: String): DailyCheckInStatus = unexpected("dailyCheckInStatus")

    override fun dailyCheckIn(userId: String, dailyId: Int) = unexpected("dailyCheckIn")

    override fun weeklyPicks(): WeeklyPicks = unexpected("weeklyPicks")

    override fun weeklyPicksDetail(categoryId: String): WeeklyPicksDetail = unexpected("weeklyPicksDetail")

    override fun notifications(): NotificationPage = unexpected("notifications")

    override fun markNotification(id: String, read: Boolean) = unexpected("markNotification")

    override fun trackingList(page: Int): TrackingPage = unexpected("trackingList")

    override fun tasks(type: TaskType, filter: TaskFilter): TaskPage = unexpected("tasks")

    override fun claimTask(type: TaskType, uid: String, taskId: String): String? = unexpected("claimTask")

    override fun imageHost(): String = unexpected("imageHost")

    override fun userProfile(uid: String): UserProfile = unexpected("userProfile")

    override fun editUserProfile(uid: String, fields: Map<String, String>): UserProfile = unexpected("editUserProfile")

    override fun watchHistory(page: Int): List<SearchResult> = unexpected("watchHistory")

    override fun deleteWatchHistory(id: String) = unexpected("deleteWatchHistory")

    override fun tagsFavorite(): List<TagFavorite> = unexpected("tagsFavorite")

    override fun addFavoriteTags(tags: List<String>) = unexpected("addFavoriteTags")

    override fun removeFavoriteTags(tags: List<String>) = unexpected("removeFavoriteTags")

    override fun manageFavoriteFolder(
        action: FavoriteFolderAction,
        folderId: String,
        name: String,
        albumId: String,
    ): FolderOperation = unexpected("manageFavoriteFolder")

    override fun usedDomain(): String? = unexpected("usedDomain")

    override fun domainsReady(): Boolean = unexpected("domainsReady")

    override fun domainLatency(): Map<String, Int> = unexpected("domainLatency")

    override fun reprobeDomains() = unexpected("reprobeDomains")

    override fun toggleFavorite(albumId: String, folderId: String) = unexpected("toggleFavorite")

    override fun novelList(order: String, page: Int): NovelPage = unexpected("novelList")

    override fun searchNovels(query: String): NovelPage = unexpected("searchNovels")

    override fun novelDetail(novelId: String): NovelDetail = unexpected("novelDetail")

    override fun novelChapter(chapterId: String, lang: String): NovelChapter = unexpected("novelChapter")

    override fun toggleNovelLike(novelId: String) = unexpected("toggleNovelLike")

    override fun toggleNovelFavorite(novelId: String) = unexpected("toggleNovelFavorite")

    override fun novelComments(novelId: String, page: Int): CommentPage = unexpected("novelComments")

    override fun postNovelComment(novelId: String, text: String, chapterId: String) = unexpected("postNovelComment")

    override fun replyNovelComment(
        novelId: String,
        text: String,
        parentCommentId: String,
        chapterId: String,
    ) = unexpected("replyNovelComment")

    override fun creatorAuthors(page: Int, query: String): CreatorPage = unexpected("creatorAuthors")

    override fun creatorWorks(
        page: Int,
        query: String,
        lang: String,
        source: String,
    ): CreatorWorkPage = unexpected("creatorWorks")

    override fun creatorAuthorWorks(
        creatorId: String,
        lang: String,
        source: String,
        page: Int,
    ): CreatorAuthorWorks = unexpected("creatorAuthorWorks")

    override fun creatorWork(workId: String): CreatorWork = unexpected("creatorWork")
}
