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

/** 追更通知正文里的单条作品更新，字段名与接口 JSON 一致。 */
data class NotificationUpdate(
    val comicId: String?,
    val comicTitle: String,
    val updateDate: String?,
)

/** 通知正文形态：站内通知是 HTML（转纯文本渲染），追更通知是更新条目数组。 */
sealed interface NotificationBody {
    /** 纯文本正文；content 缺失时为空串。 */
    data class Text(val text: String) : NotificationBody

    /** 追更通知的更新条目；接口返回空数组时 items 为空。 */
    data class Updates(val items: List<NotificationUpdate>) : NotificationBody
}

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

/** 评论正文分段：原版把正文按「[ 我推荐这本书 JMxxx ]」拆成普通文本与作品链接。 */
sealed interface CommentSegment {
    data class Text(val text: String) : CommentSegment

    /** 推荐作品链接：albumId 已去掉 JM 前缀，label 保留原样方括号文案。 */
    data class Album(val albumId: String, val label: String) : CommentSegment
}

data class CommentItem(
    val id: String,
    val author: String,
    val content: String,
    val postDate: String?,
    val likes: Int,
    val replies: List<CommentItem>,
    /** 所属作品 ID；闲聊帖为 0/空，回复时需要它定位接口实体。 */
    val albumId: String? = null,
    /** 头像绝对地址；接口未给时为空。 */
    val avatarUrl: String? = null,
    /** 用户等级数值，对应「Lv.N」。 */
    val level: Int? = null,
    /** 等级称号，与等级数值并排展示。 */
    val levelName: String? = null,
    /** 徽章图片绝对地址。 */
    val badges: List<String> = emptyList(),
    /** 关联作品名（形如 JM123456）；论坛条目底部的可点作品按钮用它。 */
    val albumName: String? = null,
    /** 正文分段；空表表示调用方直接展示 content。 */
    val body: List<CommentSegment> = emptyList(),
)

data class CommentPage(
    val total: Int,
    val items: List<CommentItem>,
)

/** 论坛范围：对应原版 ForumTabItems 的三档（显示全部 / 漫画评论 / 闲聊大厅）。 */
enum class ForumScope(val label: String) {
    ALL("全部"),
    MANHUA("漫画评论"),
    CHAT("闲聊"),
}

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

/** 个人资料（username/email 只读，其余可编辑）。字段顺序与库 JmUserProfile 一致。 */
data class UserProfile(
    val username: String,
    val email: String,
    val nickname: String,
    val lastName: String,
    val firstName: String,
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
    val infoHere: String,
    val collections: String,
    val ideal: String,
    val erogenic: String,
    val favorite: String,
    val hate: String,
) {
    /** 可编辑字段：键与库 JmUserProfile 记录名一致（服务端表单字段）；空值也提交以便清空。 */
    fun editableFields(): Map<String, String> = linkedMapOf(
        "nickname" to nickname,
        "lastName" to lastName,
        "firstName" to firstName,
        "birthday" to birthday,
        "relations" to relations,
        "sexuality" to sexuality,
        "website" to website,
        "birthPlace" to birthPlace,
        "city" to city,
        "country" to country,
        "occupation" to occupation,
        "company" to company,
        "school" to school,
        "aboutMe" to aboutMe,
        "infoHere" to infoHere,
        "collections" to collections,
        "ideal" to ideal,
        "erogenic" to erogenic,
        "favorite" to favorite,
        "hate" to hate,
    )
}

/** 收藏标签：updatedAt 为服务端相对时间文案。 */
data class TagFavorite(
    val tag: String,
    val updatedAt: String?,
)

/** 小说列表排序：对应原版 8 档排序中接口可表达的部分（o 参数）。 */
enum class NovelOrder(val apiValue: String, val label: String) {
    DEFAULT("", "全部"),
    LATEST("mr", "最新"),
    MOST_VIEWED("mv", "最多点阅"),
    MOST_CHAPTERS("mp", "最多章节"),
    MOST_LIKES("tf", "最多爱心"),
}

/** 小说列表条目。 */
data class NovelMeta(
    val id: String,
    val name: String,
    val author: String,
    val coverUrl: String?,
    val liked: Boolean,
    val favorite: Boolean,
    /** Unix 秒。 */
    val updateAt: Long,
    /** 点赞数文本，接口未提供时为 null。 */
    val likes: String? = null,
    val lastChapterIndex: String? = null,
    val lastChapterTitle: String? = null,
)

/** 小说列表页；接口只返回总数与当页条目，无总页数。 */
data class NovelPage(
    val items: List<NovelMeta>,
    val total: Int,
)

/** 小说详情里的章节条目。 */
data class NovelChapterMeta(
    val ncid: String,
    val title: String,
    val sort: String?,
    /** 是否为新章节（原版 new 标记）。 */
    val isNew: Boolean,
    /** 是否已购买。 */
    val purchased: Boolean,
    /** 是否需要购买才能阅读。 */
    val needBuy: Boolean,
)

/** 小说详情。 */
data class NovelDetail(
    val id: String,
    val name: String,
    val author: String,
    val coverUrl: String?,
    /** 上架时间（Unix 秒）。 */
    val addtime: Long,
    val description: String,
    val totalViews: String?,
    val likes: String?,
    val isEnd: Boolean,
    val serialStatus: String,
    val tags: List<String>,
    val liked: Boolean,
    val favorite: Boolean,
    val chapters: List<NovelChapterMeta>,
    val related: List<NovelMeta>,
)

/** 小说章节正文。 */
data class NovelChapter(
    val ncid: String,
    val novelId: String,
    val title: String,
    /** 已去标签与实体的纯文本。 */
    val contentText: String,
    val lastChapterTitle: String?,
    val totalFavorites: String?,
    val totalViews: String?,
    val totalLikes: String?,
    val addtime: String?,
    val favorite: Boolean,
    val liked: Boolean,
)

/** 创作者作品来源筛选：对应原版语言/来源两个下拉，选项由接口 filters 下发。 */
data class CreatorFilters(
    val languages: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = languages.isEmpty() && sources.isEmpty()
}

/** 创作者/作者摘要；头像与背景为完整 URL，缺失时为 null。 */
data class CreatorMeta(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    /** 服务端相对时间文案，如 "25 days ago"。 */
    val updateText: String?,
)

/** 创作者列表分页。 */
data class CreatorPage(
    val items: List<CreatorMeta>,
    val total: Int,
)

/** 创作者作品摘要。 */
data class CreatorWorkMeta(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val platform: String?,
    val workDate: String?,
    val authorName: String?,
    val authorId: String?,
)

/** 作品列表分页；filters 为接口下发的可选筛选值。 */
data class CreatorWorkPage(
    val items: List<CreatorWorkMeta>,
    val total: Int,
    val filters: CreatorFilters,
)

/** 创作者详情页的关联作品。 */
data class CreatorRelatedWork(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val workDate: String?,
    val platform: String?,
)

/** 赞助平台入口。 */
data class CreatorSponsor(
    val name: String,
    val url: String?,
)

/** 创作者主页：头像/背景/赞助入口/作品集合。 */
data class CreatorAuthorWorks(
    val authorName: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val sponsors: List<CreatorSponsor>,
    val works: List<CreatorRelatedWork>,
    val filters: CreatorFilters,
)

/** 创作者作品详情：描述 + 整页图片；related 供底部相关作品入口。 */
data class CreatorWork(
    val id: String,
    val title: String,
    val authorName: String?,
    val workDate: String?,
    /** 已去标签的纯文本描述。 */
    val contentText: String,
    val images: List<String>,
    val related: List<CreatorRelatedWork>,
)

