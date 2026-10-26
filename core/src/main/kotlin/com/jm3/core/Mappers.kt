package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.model.JmAlbum
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmPhoto
import io.github.jukomu.jmcomic.api.model.JmPhotoMeta

// 库为 Java record，组件访问器无 get 前缀，统一函数调用语法。
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
)

fun JmPhoto.toPhotoPages(): PhotoPages = PhotoPages(
    photoId = id(),
    title = title() ?: "",
    images = images().orEmpty().mapIndexed { index, image ->
        PageImage(
            photoId = image.photoId() ?: id(),
            scrambleId = image.scrambleId() ?: scrambleId() ?: "",
            index = index,
            filename = image.filename() ?: "",
            url = image.url() ?: "",
        )
    },
)
