package com.jm3.core

import com.jm3.core.model.NovelChapter
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.NovelDetail
import com.jm3.core.model.NovelMeta
import com.jm3.core.model.NovelPage
import io.github.jukomu.jmcomic.api.model.JmNovelChapter
import io.github.jukomu.jmcomic.api.model.JmNovelChapterMeta
import io.github.jukomu.jmcomic.api.model.JmNovelDetail
import io.github.jukomu.jmcomic.api.model.JmNovelMeta
import io.github.jukomu.jmcomic.api.model.JmNovelPage
import io.github.jukomu.jmcomic.api.model.JmRelatedNovel

fun JmNovelMeta.toNovelMeta(covers: CoverResolver = CoverResolver.Unready): NovelMeta = NovelMeta(
    id = getId().orEmpty(),
    name = getName().orEmpty(),
    author = getAuthor().orEmpty(),
    coverUrl = covers.image(getImage()),
    liked = isLiked(),
    favorite = getIsFavorite() == true,
    updateAt = getUpdateAt(),
    likes = getLikes()?.takeIf { it.isNotBlank() },
    lastChapterIndex = getLastChapterIndex()?.takeIf { it.isNotBlank() },
    lastChapterTitle = getLastChapterTitle()?.takeIf { it.isNotBlank() },
)

fun JmRelatedNovel.toNovelMeta(covers: CoverResolver = CoverResolver.Unready): NovelMeta = NovelMeta(
    id = getId().orEmpty(),
    name = getName().orEmpty(),
    author = getAuthor().orEmpty(),
    coverUrl = covers.image(getImage()),
    liked = false,
    favorite = getIsFavorite() == true,
    updateAt = getChapterUpdateAt(),
    likes = getLikes()?.takeIf { it.isNotBlank() },
    lastChapterIndex = getLastChapterIndex()?.takeIf { it.isNotBlank() },
    lastChapterTitle = getLastChapterTitle()?.takeIf { it.isNotBlank() },
)

fun JmNovelPage.toNovelPage(covers: CoverResolver = CoverResolver.Unready): NovelPage = NovelPage(
    items = getList().orEmpty().map { it.toNovelMeta(covers) },
    total = getTotal(),
)

fun JmNovelChapterMeta.toNovelChapterMeta(): NovelChapterMeta = NovelChapterMeta(
    ncid = getNCID().orEmpty(),
    title = getTitle().orEmpty().ifBlank { getName().orEmpty() },
    sort = getSort()?.takeIf { it.isNotBlank() },
    isNew = isNew(),
    purchased = isPurchased(),
    needBuy = getIsNeedBuyNc() == "1" && !isPurchased(),
)

fun JmNovelDetail.toNovelDetail(covers: CoverResolver = CoverResolver.Unready): NovelDetail = NovelDetail(
    id = getId().orEmpty(),
    name = getName().orEmpty(),
    author = getAuthor().orEmpty(),
    coverUrl = covers.image(getImages()),
    addtime = getAddtime(),
    description = getDescription().orEmpty(),
    totalViews = getTotalViews()?.takeIf { it.isNotBlank() },
    likes = getLikes()?.takeIf { it.isNotBlank() },
    isEnd = getIsEnd() == "1",
    serialStatus = getSerialStatus().orEmpty(),
    tags = getTags().orEmpty(),
    liked = isLiked(),
    favorite = getIsFavorite() == true,
    chapters = getSeries().orEmpty().map { it.toNovelChapterMeta() },
    related = getRelatedList().orEmpty().map { it.toNovelMeta(covers) },
)

fun JmNovelChapter.toNovelChapter(): NovelChapter = NovelChapter(
    ncid = getNcid().toString(),
    novelId = getNid().orEmpty(),
    title = getTitle().orEmpty().ifBlank { getName().orEmpty() },
    contentText = getContentText().orEmpty().ifBlank { getContent().orEmpty() },
    lastChapterTitle = getLastChapterTitle()?.takeIf { it.isNotBlank() },
    totalFavorites = getTotalFavorites()?.takeIf { it.isNotBlank() },
    totalViews = getTotalViews()?.takeIf { it.isNotBlank() },
    totalLikes = getTotalLikes()?.takeIf { it.isNotBlank() },
    addtime = getAddtime()?.takeIf { it.isNotBlank() },
    favorite = getIsFavorite() == true,
    liked = isLiked(),
)
