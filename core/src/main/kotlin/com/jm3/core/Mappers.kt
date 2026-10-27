package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.CategoryNode
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.model.Jm3User
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.model.TagFavorite
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TrackingItem
import com.jm3.core.model.TrackingPage
import com.jm3.core.model.UserProfile
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicks
import com.jm3.core.model.WeeklyPicksDetail
import io.github.jukomu.jmcomic.api.enums.Category
import io.github.jukomu.jmcomic.api.enums.FavoriteFolderType
import io.github.jukomu.jmcomic.api.enums.OrderBy
import io.github.jukomu.jmcomic.api.enums.SearchMainTag
import io.github.jukomu.jmcomic.api.enums.TimeOption
import io.github.jukomu.jmcomic.api.model.JmAlbum
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.FavoriteQuery
import io.github.jukomu.jmcomic.api.model.JmCategoryList
import io.github.jukomu.jmcomic.api.model.JmCategoryListItem
import io.github.jukomu.jmcomic.api.model.JmComment
import io.github.jukomu.jmcomic.api.model.JmCommentList
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInStatus
import io.github.jukomu.jmcomic.api.model.JmFavoriteFolderResult
import io.github.jukomu.jmcomic.api.model.JmFavoritePage
import io.github.jukomu.jmcomic.api.model.JmNotificationPage
import io.github.jukomu.jmcomic.api.model.JmTagFavorite
import io.github.jukomu.jmcomic.api.model.JmTaskItem
import io.github.jukomu.jmcomic.api.model.JmTaskList
import io.github.jukomu.jmcomic.api.model.JmTrackingPage
import io.github.jukomu.jmcomic.api.model.JmUserProfile
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksDetail
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksList
import io.github.jukomu.jmcomic.api.model.JmImage
import io.github.jukomu.jmcomic.api.model.JmPhoto
import io.github.jukomu.jmcomic.api.model.JmPhotoMeta
import io.github.jukomu.jmcomic.api.model.JmUserInfo
import io.github.jukomu.jmcomic.api.model.SearchQuery
import kotlin.math.roundToInt

// 库为 Java record，组件访问器无 get 前缀，统一函数调用语法。

fun JmUserInfo.toUser(): Jm3User = Jm3User(
    uid = uid,
    username = username,
    email = email,
    emailVerified = emailVerified,
    photoUrl = avatarUrl,
    coin = coin,
    albumFavorites = albumFavorites,
    maxAlbumFavorites = maxAlbumFavorites,
    level = level,
    levelName = levelName,
    nextLevelExp = nextLevelExp,
    currentExp = currentExp,
    expPercent = expPercent.roundToInt(),
)
fun JmAlbumMeta.toSearchResult(coverUrl: String? = null): SearchResult = SearchResult(
    id = id(),
    title = title() ?: "",
    authors = authors().orEmpty(),
    tags = tags().orEmpty(),
    coverUrl = coverUrl,
)

fun JmPhotoMeta.toChapter(): Chapter = Chapter(
    id = id(),
    title = title() ?: "",
    sortOrder = sortOrder(),
)

fun JmAlbum.toAlbumDetail(
    coverUrl: String? = null,
    relatedCoverUrl: (JmAlbumMeta) -> String? = { null },
): AlbumDetail = AlbumDetail(
    id = id(),
    title = title() ?: "",
    description = description() ?: "",
    authors = authors().orEmpty(),
    tags = tags().orEmpty(),
    works = works().orEmpty(),
    actors = actors().orEmpty(),
    likes = likes(),
    views = views(),
    coverUrl = coverUrl,
    chapters = photoMetas().orEmpty().map { it.toChapter() },
    favorited = isFavorite(),
    pageCount = pageCount(),
    commentCount = commentCount(),
    liked = isLiked(),
    related = relatedAlbums().orEmpty().map { it.toSearchResult(coverUrl = relatedCoverUrl(it)) },
)

fun JmPhoto.toPhotoPages(): PhotoPages = PhotoPages(
    photoId = id(),
    albumId = albumId() ?: id(),
    title = title() ?: "",
    images = images().orEmpty().mapIndexed { index, image ->
        PageImage(
            photoId = image.photoId() ?: id(),
            scrambleId = image.scrambleId() ?: scrambleId() ?: "",
            index = index,
            filename = image.filename() ?: "",
            url = image.getDownloadUrl() ?: "",
        )
    },
)

fun BrowseFilter.toSearchQuery(page: Int): SearchQuery {
    val category = Category.entries.firstOrNull { it.value == category.apiValue } ?: Category.ALL
    val orderBy = OrderBy.entries.firstOrNull { it.value == order.apiValue } ?: OrderBy.LATEST
    val time = TimeOption.entries.firstOrNull { it.value == time.apiValue } ?: TimeOption.ALL
    return SearchQuery.Builder()
        .text("")
        .category(category)
        .orderBy(orderBy)
        .time(time)
        .page(page)
        .build()
}

/** 排序值映射：core 枚举值即库 OrderBy 的 API 值。 */
fun BrowseOrder.toOrderBy(): OrderBy =
    OrderBy.entries.firstOrNull { it.value == apiValue } ?: OrderBy.LATEST

/** 搜索范围映射到库的主标签。 */
fun SearchScope.toMainTag(): SearchMainTag = when (this) {
    SearchScope.SITE -> SearchMainTag.SITE_SEARCH
    SearchScope.WORK -> SearchMainTag.WORK
    SearchScope.AUTHOR -> SearchMainTag.AUTHOR
    SearchScope.TAG -> SearchMainTag.TAG
    SearchScope.ACTOR -> SearchMainTag.ACTOR
}

/** 时间范围映射：core 枚举值即库 TimeOption 的 API 值。 */
fun BrowseTime.toTimeOption(): TimeOption =
    TimeOption.entries.firstOrNull { it.value == apiValue } ?: TimeOption.ALL

/** 关键词/标签搜索查询；mainTag 决定站内搜索或标签检索，time 由服务端过滤时间范围。 */
fun searchQuery(
    text: String,
    page: Int,
    mainTag: SearchMainTag,
    order: BrowseOrder,
    time: BrowseTime = BrowseTime.ALL,
): SearchQuery = SearchQuery.Builder()
    .mainTag(mainTag)
    .text(text)
    .orderBy(order.toOrderBy())
    .time(time.toTimeOption())
    .page(page)
    .build()

fun PageImage.toJmImage(): JmImage = JmImage(
    photoId,
    scrambleId,
    filename,
    url,
    "",
    index + 1,
)

// 评论字段可能缺失，逐级回退；回复列表递归映射。
fun JmComment.toCommentItem(): CommentItem = CommentItem(
    id = commentId() ?: "",
    author = nickname() ?: username() ?: name() ?: "匿名",
    content = content() ?: "",
    postDate = postDate(),
    likes = likes(),
    replies = replys().orEmpty().map { it.toCommentItem() },
    albumId = aid()?.takeIf { it.isNotBlank() && it != "0" },
)

fun JmCommentList.toCommentPage(): CommentPage = CommentPage(
    total = total(),
    items = list().orEmpty().map { it.toCommentItem() },
)

/** 收藏夹列表查询：folderId=0 表示全部收藏。 */
fun favoritesQuery(folderId: Int, page: Int): FavoriteQuery =
    FavoriteQuery.Builder().folderId(folderId).page(page).build()

/** 收藏列表页：folders 为收藏夹 id→名称；coverUrlFor 解析封面地址（由网关按图片域名构造）。 */
fun JmFavoritePage.toFavoritePage(coverUrlFor: (JmAlbumMeta) -> String? = { null }): FavoritePage = FavoritePage(
    currentPage = currentPage,
    totalPages = totalPages,
    folders = folderList.orEmpty(),
    folderId = folderId,
    items = content.orEmpty().map { it.toSearchResult(coverUrl = coverUrlFor(it)) },
)

/** 签到状态：signedDays/totalDays 由 record 记录扁平统计；未签到/无标记不算签到。 */
fun JmDailyCheckInStatus.toDailyCheckInStatus(): DailyCheckInStatus = DailyCheckInStatus(
    dailyId = dailyId,
    eventName = eventName.orEmpty(),
    currentProgress = currentProgress.orEmpty(),
    signedDays = record.orEmpty().flatten().count { it.signed == true },
    totalDays = record.orEmpty().flatten().size,
)

/** 每周必看期数列表；null 分类按空表处理。 */
fun JmWeeklyPicksList.toWeeklyPicks(): WeeklyPicks = WeeklyPicks(
    periods = categories().orEmpty().map { category ->
        WeeklyPeriod(
            id = category.id().orEmpty(),
            title = category.title().orEmpty(),
            time = category.time().orEmpty(),
        )
    },
)

/** 每周必看某期结果；封面由网关按图片域名统一构造。 */
fun JmWeeklyPicksDetail.toWeeklyPicksDetail(coverUrlFor: (JmAlbumMeta) -> String? = { null }): WeeklyPicksDetail =
    WeeklyPicksDetail(
        total = total(),
        items = list().orEmpty().map { it.toSearchResult(coverUrl = coverUrlFor(it)) },
    )

/** 通知列表；标题/正文缺失时降级为空串，已读态直接透传。 */
fun JmNotificationPage.toNotificationPage(): NotificationPage = NotificationPage(
    total = total(),
    items = list().orEmpty().map { item ->
        NotificationItem(
            id = item.id().orEmpty(),
            title = item.title().orEmpty(),
            content = item.content().orEmpty(),
            time = item.time(),
            read = item.read(),
            type = item.type(),
        )
    },
)

/** 连载追踪列表；totalCnt 为字符串类型，无法解析时按当前页条数计。 */
fun JmTrackingPage.toTrackingPage(): TrackingPage {
    val items = item().orEmpty().map { entry ->
        TrackingItem(
            id = entry.id().orEmpty(),
            name = entry.name().orEmpty(),
            coverUrl = entry.image()?.takeIf { it.isNotBlank() },
            updateAt = entry.updateAt()?.takeIf { it.isNotBlank() },
        )
    }
    return TrackingPage(
        total = totalCnt()?.toIntOrNull() ?: items.size,
        items = items,
    )
}

/** 收藏夹操作结果：服务端 status 非 ok 时携带 msg 作为失败原因。 */
fun JmFavoriteFolderResult.toFolderOperation(): FolderOperation =
    FolderOperation(ok = isOk(), message = msg()?.takeIf { it.isNotBlank() })

/** 收藏夹动作映射：core 枚举值即库 FavoriteFolderType 的 API 值。 */
fun FavoriteFolderAction.toFavoriteFolderType(): FavoriteFolderType =
    FavoriteFolderType.entries.firstOrNull { it.value == apiValue } ?: FavoriteFolderType.ADD

/** 分类条目：总数可能为字符串；缺失按 0。子分类同样递归成节点，供「更多分类」弹窗使用。 */
fun JmCategoryListItem.toCategoryNode(): CategoryNode = CategoryNode(
    id = id().orEmpty(),
    name = name().orEmpty(),
    slug = slug().orEmpty(),
    totalAlbums = totalAlbums()?.toIntOrNull() ?: 0,
    children = subCategories().map { sub ->
        CategoryNode(
            id = sub.cid().orEmpty(),
            name = sub.name().orEmpty(),
            slug = sub.slug().orEmpty(),
        )
    },
)

/** 分类树：一级分类 + 标签区块。 */
fun JmCategoryList.toCategoryTree(): CategoryTree = CategoryTree(
    categories = categories().map { it.toCategoryNode() },
    blocks = blocks().map { block ->
        CategoryBlock(
            title = block.title().orEmpty(),
            tags = block.content().orEmpty(),
        )
    },
)

/** 任务条目：金币为文本，无法解析时按 0。 */
fun JmTaskItem.toTaskItem(): TaskItem = TaskItem(
    id = id().orEmpty(),
    name = name().orEmpty(),
    content = content().orEmpty(),
    coin = coin()?.toIntOrNull() ?: 0,
    done = isDone,
    type = type(),
)

/** 任务列表：status 非 ok 时把 msg 作为失败原因带到 UI。 */
fun JmTaskList.toTaskPage(): TaskPage = TaskPage(
    items = list().orEmpty().map { it.toTaskItem() },
    message = msg()?.takeIf { it.isNotBlank() && !"ok".equals(status(), ignoreCase = true) },
)

/** 个人资料：空字段统一为 empty string，避免 UI 出现 null 分支。 */
fun JmUserProfile.toUserProfile(): UserProfile = UserProfile(
    username = username().orEmpty(),
    email = email().orEmpty(),
    nickname = nickname().orEmpty(),
    lastName = lastName().orEmpty(),
    firstName = firstName().orEmpty(),
    birthday = birthday().orEmpty(),
    relations = relations().orEmpty(),
    sexuality = sexuality().orEmpty(),
    website = website().orEmpty(),
    birthPlace = birthPlace().orEmpty(),
    city = city().orEmpty(),
    country = country().orEmpty(),
    occupation = occupation().orEmpty(),
    company = company().orEmpty(),
    school = school().orEmpty(),
    aboutMe = aboutMe().orEmpty(),
    infoHere = infoHere().orEmpty(),
    collections = collections().orEmpty(),
    ideal = ideal().orEmpty(),
    erogenic = erogenic().orEmpty(),
    favorite = favorite().orEmpty(),
    hate = hate().orEmpty(),
)

fun JmTagFavorite.toTagFavorite(): TagFavorite = TagFavorite(
    tag = tag().orEmpty(),
    updatedAt = updatedAt()?.takeIf { it.isNotBlank() },
)
