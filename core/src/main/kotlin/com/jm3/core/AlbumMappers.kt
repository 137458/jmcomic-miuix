package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.model.JmAlbum
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmImage
import io.github.jukomu.jmcomic.api.model.JmPhoto
import io.github.jukomu.jmcomic.api.model.JmPhotoMeta

// 库为 Java record，组件访问器无 get 前缀，统一函数调用语法。

fun JmAlbumMeta.toSearchResult(covers: CoverResolver = CoverResolver.Unready): SearchResult = SearchResult(
    id = id(),
    title = title() ?: "",
    authors = authors().orEmpty(),
    tags = tags().orEmpty(),
    coverUrl = covers.resolve(id()).urlOrNull,
    // 主分类缺失时退回子分类：列表接口给的分类粒度不一致，卡片只展示一层分类。
    category = category()?.title()?.trim()?.takeIf { it.isNotBlank() }
        ?: subCategory()?.title()?.trim()?.takeIf { it.isNotBlank() },
)

fun JmPhotoMeta.toChapter(): Chapter = Chapter(
    id = id(),
    title = title() ?: "",
    sortOrder = sortOrder(),
)

fun JmAlbum.toAlbumDetail(covers: CoverResolver = CoverResolver.Unready): AlbumDetail = AlbumDetail(
    id = id(),
    title = title()?.trim() ?: "",
    description = description()?.trim() ?: "",
    authors = authors().orEmpty().map { it.trim() }.filter { it.isNotBlank() },
    tags = tags().orEmpty().map { it.trim() }.filter { it.isNotBlank() },
    works = works().orEmpty().map { it.trim() }.filter { it.isNotBlank() },
    actors = actors().orEmpty().map { it.trim() }.filter { it.isNotBlank() },
    likes = likes(),
    views = views(),
    coverUrl = covers.resolve(id()).urlOrNull,
    chapters = photoMetas().orEmpty().map { it.toChapter() },
    favorited = isFavorite(),
    pageCount = pageCount(),
    commentCount = commentCount(),
    liked = isLiked(),
    related = relatedAlbums().orEmpty().map { it.toSearchResult(covers) },
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

fun PageImage.toJmImage(): JmImage = JmImage(
    photoId,
    scrambleId,
    filename,
    url,
    "",
    index + 1,
)
