package com.jm3.app.miuix

import com.jm3.core.model.SearchScope
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Login : Route

    @Serializable
    data object Favorites : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Shelf : Route

    @Serializable
    data object Settings : Route

    /** 检查更新（二级页，数据源为远端 Release 信息）。 */
    @Serializable
    data object Update : Route

    /** 每周必看（原版 Week 页）。 */
    @Serializable
    data object Week : Route

    /** 站内信箱/通知。 */
    @Serializable
    data object Notifications : Route

    /** 连载追踪列表。 */
    @Serializable
    data object Tracking : Route

    /** 成就/任务（称号与勋章）。 */
    @Serializable
    data object Tasks : Route

    /** 个人资料查看与编辑。 */
    @Serializable
    data object Profile : Route

    /** 服务端观看记录。 */
    @Serializable
    data object WatchHistory : Route

    /** 社区论坛（全部/漫画评论/闲聊三档）。 */
    @Serializable
    data object Forum : Route

    @Serializable
    data class AlbumDetail(
        val albumId: String,
        val initialTitle: String? = null,
        val initialCoverUrl: String? = null,
    ) : Route

    @Serializable
    data class Reader(val photoId: String, val startPage: Int? = null) : Route

    /** 小说列表（原版 Novels 页）。 */
    @Serializable
    data object Novel : Route

    /** 小说详情：目录/相关/评论三档。 */
    @Serializable
    data class NovelDetail(val novelId: String) : Route

    /** 小说阅读器：记录起始章节，返回详情页而非逐章回退。 */
    @Serializable
    data class NovelReader(val novelId: String, val chapterId: String) : Route

    /** 禁漫书库（原版 Library：创作者/作品双 Tab）。 */
    @Serializable
    data object Library : Route

    /** 创作者主页：头像/背景/赞助入口/作品集合。 */
    @Serializable
    data class Creator(val creatorId: String) : Route

    /** 创作者作品详情：描述 + 整页图片 + 相关作品。 */
    @Serializable
    data class CreatorWork(val workId: String) : Route

    /** 独立搜索结果页：标签/作者/作品/人物点击进入；scope 对应原版搜索类型。 */
    @Serializable
    data class Search(val query: String, val scope: SearchScope = SearchScope.SITE) : Route
}
