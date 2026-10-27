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
    /** 结果总数；0 表示库未提供。 */
    val totalItems: Int = 0,
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
    val favorited: Boolean,
    /** 总页数；接口未返回时为 0。 */
    val pageCount: Int = 0,
    val commentCount: Int = 0,
    /** 当前用户是否已点赞。 */
    val liked: Boolean = false,
    /** 更多相关作品。 */
    val related: List<SearchResult> = emptyList(),
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
    /** 收藏夹 id→名称；空表表示库未返回。 */
    val folders: Map<String, String> = emptyMap(),
    /** 当前页所属收藏夹；0 表示全部收藏。 */
    val folderId: Int = 0,
)

/** 签到状态：signedDays/totalDays 由 record 记录扁平统计，不解析 date 做“今日”判断。 */
data class DailyCheckInStatus(
    val dailyId: Int,
    val eventName: String,
    val currentProgress: String,
    val signedDays: Int,
    val totalDays: Int,
)

/** 单个 API 域名的探测延迟（毫秒），供设置页排序展示。 */
data class DomainLatency(
    val domain: String,
    val latencyMs: Int,
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

/** 搜索范围（对应原版搜索类型）：站内/作品/作者/标签/登场人物。 */
enum class SearchScope(val label: String) {
    SITE("综合"),
    WORK("作品"),
    AUTHOR("作者"),
    TAG("标签"),
    ACTOR("登场人物"),
}

/** 分类/榜单浏览筛选条件。 */
data class BrowseFilter(
    val category: BrowseCategory = BrowseCategory.ALL,
    val order: BrowseOrder = BrowseOrder.LATEST,
    val time: BrowseTime = BrowseTime.ALL,
)

/** 每周必看期数（原版 Week 页的期数菜单）。 */
data class WeeklyPeriod(
    val id: String,
    val title: String,
    val time: String,
)

/** 每周必看期数列表；接口不可用时 periods 为空。 */
data class WeeklyPicks(
    val periods: List<WeeklyPeriod>,
)

/** 每周必看某一期的作品集合（接口一次返回全部，不分页）。 */
data class WeeklyPicksDetail(
    val total: Int,
    val items: List<SearchResult>,
)

/** 站内通知/信箱单条记录。 */
data class NotificationItem(
    val id: String,
    val title: String,
    val content: String,
    val time: String?,
    val read: Boolean,
    val type: String?,
)

data class NotificationPage(
    val total: Int,
    val items: List<NotificationItem>,
)

/** 连载追踪条目；updateAt 为 Unix 秒字符串，接口未提供时不展示时间。 */
data class TrackingItem(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val updateAt: String?,
)

data class TrackingPage(
    val total: Int,
    val items: List<TrackingItem>,
)

/** 收藏夹管理动作，apiValue 与库 FavoriteFolderType 一致。 */
enum class FavoriteFolderAction(val apiValue: String) {
    ADD("add"),
    EDIT("edit"),
    MOVE("move"),
    DELETE("del"),
}

/** 收藏夹操作结果：ok=false 时 message 为服务端返回的失败原因。 */
data class FolderOperation(
    val ok: Boolean,
    val message: String?,
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

/** 分类条目：slug 用于标签检索，children 为子分类（原版「更多分类」弹窗）。 */
data class CategoryNode(
    val id: String,
    val name: String,
    val slug: String,
    val totalAlbums: Int = 0,
    val children: List<CategoryNode> = emptyList(),
)

/** 标签区块：如「主题A漫」「角色/扮演」，内容为可点击标签。 */
data class CategoryBlock(
    val title: String,
    val tags: List<String>,
)

/** 分类树：一级分类 + 标签区块；接口不可用时均为空表。 */
data class CategoryTree(
    val categories: List<CategoryNode>,
    val blocks: List<CategoryBlock>,
)

/** 任务类型：称号 / 勋章（原版成就页两组）。 */
enum class TaskType(val apiValue: String, val label: String) {
    TITLE("title", "称号"),
    BADGE("badge", "勋章"),
}

/** 任务筛选：我的 / 全部。 */
enum class TaskFilter(val apiValue: String, val label: String) {
    MY("my", "我的"),
    ALL("all", "全部"),
}

/** 成就/任务条目；coin 为奖励金币文本，无法解析时为 0。 */
data class TaskItem(
    val id: String,
    val name: String,
    val content: String,
    val coin: Int,
    val done: Boolean,
    val type: String?,
)

/** 任务列表；message 为服务端失败原因。 */
data class TaskPage(
    val items: List<TaskItem>,
    val message: String? = null,
)

/** 个人资料（username/email 只读，其余可编辑）。 */
data class UserProfile(
    val username: String,
    val email: String,
    val nickname: String,
    val birthday: String,
    val relations: String,
    val sexuality: String,
    val website: String,
    val birthPlace: String,
    val city: String,
    val country: String,
    val occupation: String,
    val company: String,
    val school: String,
    val aboutMe: String,
    val favorite: String,
    val hate: String,
) {
    /** 可编辑字段：库 editUserProfile 的键 → 值；空值也提交以便清空。 */
    fun editableFields(): Map<String, String> = linkedMapOf(
        "nickname" to nickname,
        "birthday" to birthday,
        "relations" to relations,
        "sexuality" to sexuality,
        "website" to website,
        "birthplace" to birthPlace,
        "city" to city,
        "country" to country,
        "occupation" to occupation,
        "company" to company,
        "school" to school,
        "about_me" to aboutMe,
        "favorite" to favorite,
        "hate" to hate,
    )
}

/** 收藏标签：updatedAt 为服务端相对时间文案。 */
data class TagFavorite(
    val tag: String,
    val updatedAt: String?,
)
