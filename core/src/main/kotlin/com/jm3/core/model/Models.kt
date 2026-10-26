package com.jm3.core.model

import kotlinx.serialization.Serializable

data class SearchResult(
    val id: String,
    val title: String,
    val authors: List<String>,
    val tags: List<String>,
    val coverUrl: String?,
)

data class SearchPage(
    val currentPage: Int,
    val totalPages: Int,
    val items: List<SearchResult>,
)

data class Chapter(
    val id: String,
    val title: String,
    val sortOrder: Int,
)

data class AlbumDetail(
    val id: String,
    val title: String,
    val description: String,
    val authors: List<String>,
    val tags: List<String>,
    val works: List<String>,
    val actors: List<String>,
    val likes: String?,
    val views: String?,
    val coverUrl: String?,
    val chapters: List<Chapter>,
)

@Serializable
data class PageImage(
    val photoId: String,
    val scrambleId: String,
    val index: Int,
    val filename: String,
    val url: String,
)

@Serializable
data class PhotoPages(
    val photoId: String,
    val albumId: String,
    val title: String,
    val images: List<PageImage>,
)

@Serializable
data class Jm3User(
    val uid: String,
    val username: String,
    val email: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
    val coin: Int,
    val albumFavorites: Int,
    val maxAlbumFavorites: Int,
    val level: Int,
    val levelName: String?,
    val nextLevelExp: Long,
    val currentExp: Long,
    val expPercent: Int,
)

/** 会话 Cookie 快照，用于本地持久化与恢复注入。 */
@Serializable
data class CookieSnapshot(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
)

/** 登录会话：用户信息 + 会话 Cookie。 */
@Serializable
data class Jm3Session(
    val user: Jm3User,
    val cookies: List<CookieSnapshot>,
)

/** 收藏列表页（folderId=0 全部收藏）。 */
data class FavoritePage(
    val currentPage: Int,
    val totalPages: Int,
    val items: List<SearchResult>,
)

/** 筛选选项公共形态，供 UI 泛型渲染。 */
interface LabeledOption {
    val apiValue: String
    val label: String
}

/** 分类筛选的主分类；apiValue 与库 Category 枚举的 value 一致。 */
enum class BrowseCategory(override val apiValue: String, override val label: String) : LabeledOption {
    ALL("0", "全部"),
    DOUJIN("doujin", "同人"),
    SINGLE("single", "单本"),
    SHORT("short", "短篇"),
    OTHER("another", "其他"),
    KOREAN("hanman", "韩漫"),
    AMERICAN("meiman", "美漫"),
    COSPLAY("doujin_cosplay", "Cosplay"),
    IMAGE_3D("3D", "3D"),
}

/** 分类/榜单的排序方式；apiValue 与库 OrderBy 枚举的 value 一致。 */
enum class BrowseOrder(override val apiValue: String, override val label: String) : LabeledOption {
    LATEST("mr", "最新"),
    MOST_VIEWED("mv", "最多观看"),
    MOST_IMAGES("mp", "图片最多"),
    MOST_LIKED("tf", "最多喜欢"),
}

/** 分类/榜单的时间范围；apiValue 与库 TimeOption 枚举的 value 一致。 */
enum class BrowseTime(override val apiValue: String, override val label: String) : LabeledOption {
    TODAY("t", "今日"),
    WEEK("w", "本周"),
    MONTH("m", "本月"),
    ALL("a", "全部"),
}

/** 分类/榜单浏览筛选条件。 */
data class BrowseFilter(
    val category: BrowseCategory = BrowseCategory.ALL,
    val order: BrowseOrder = BrowseOrder.LATEST,
    val time: BrowseTime = BrowseTime.ALL,
)

data class CommentItem(
    val id: String,
    val author: String,
    val content: String,
    val postDate: String?,
    val likes: Int,
    val replies: List<CommentItem>,
)

data class CommentPage(
    val total: Int,
    val items: List<CommentItem>,
)
