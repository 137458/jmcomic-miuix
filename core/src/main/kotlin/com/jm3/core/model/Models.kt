package com.jm3.core.model

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

data class PageImage(
    val photoId: String,
    val scrambleId: String,
    val index: Int,
    val filename: String,
    val url: String,
)

data class PhotoPages(
    val photoId: String,
    val title: String,
    val images: List<PageImage>,
)
