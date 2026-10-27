package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.Jm3User
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.enums.Category
import io.github.jukomu.jmcomic.api.enums.OrderBy
import io.github.jukomu.jmcomic.api.enums.TimeOption
import io.github.jukomu.jmcomic.api.model.JmAlbum
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.FavoriteQuery
import io.github.jukomu.jmcomic.api.model.JmComment
import io.github.jukomu.jmcomic.api.model.JmCommentList
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInStatus
import io.github.jukomu.jmcomic.api.model.JmFavoritePage
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

fun JmAlbum.toAlbumDetail(coverUrl: String? = null): AlbumDetail = AlbumDetail(
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
