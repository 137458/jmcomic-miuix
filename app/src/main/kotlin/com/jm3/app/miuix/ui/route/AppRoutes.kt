package com.jm3.app.miuix.ui.route

import com.jm3.app.miuix.Route
import com.jm3.core.model.SearchScope

/**
 * 导航目标表：全仓唯一构造 [Route] 的地方。
 *
 * 屏幕只认识语义动作（打开作品、打开阅读器、打开搜索），不直接拼路由类与参数；
 * 路由改名、加参数、或把某个二级页改成同层替换，只在这里改一次。
 *
 * @param push 压栈（普通二级页）
 * @param replaceTop 同层替换：栈顶换成新页面，返回键回到上一层而不是逐级回退
 */
internal class AppRoutes(
    private val push: (Route) -> Unit,
    private val replaceTop: (Route) -> Unit,
) {
    fun login() = push(Route.Login)

    fun favorites() = push(Route.Favorites)

    fun history() = push(Route.History)

    fun shelf() = push(Route.Shelf)

    fun settings() = push(Route.Settings)

    fun update() = push(Route.Update)

    fun week() = push(Route.Week)

    fun notifications() = push(Route.Notifications)

    fun tracking() = push(Route.Tracking)

    fun tasks() = push(Route.Tasks)

    fun profile() = push(Route.Profile)

    fun watchHistory() = push(Route.WatchHistory)

    fun forum() = push(Route.Forum)

    /** 作品详情：列表卡片点进去时不带标题与封面，交给详情页自己拉。 */
    fun album(albumId: String) = push(Route.AlbumDetail(albumId))

    /** 漫画阅读器：章节内翻页不换页，切章走 [nextChapter]。 */
    fun reader(photoId: String) = push(Route.Reader(photoId))

    /** 阅读器从指定页打开（历史记录与离线继续阅读）。 */
    fun readerAt(photoId: String, startPage: Int?) = push(Route.Reader(photoId, startPage))

    /** 阅读器切章：同层替换，返回键回到详情页。 */
    fun nextChapter(photoId: String) = replaceTop(Route.Reader(photoId))

    fun novelList() = push(Route.Novel)

    fun novelDetail(novelId: String) = push(Route.NovelDetail(novelId))

    fun novelReader(novelId: String, chapterId: String) = push(Route.NovelReader(novelId, chapterId))

    fun library() = push(Route.Library)

    fun creator(creatorId: String) = push(Route.Creator(creatorId))

    fun creatorWork(workId: String) = push(Route.CreatorWork(workId))

    fun search(query: String, scope: SearchScope) = push(Route.Search(query, scope))
}
