package com.jm3.app.miuix.data.download

import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import kotlinx.serialization.json.Json

private val downloadJson = Json { ignoreUnknownKeys = true }

/** 页列表 <-> JSON（photoId 为 key，albumId/title 以记录表为准）。 */
internal fun encodePhotoPages(photoId: String, images: List<PageImage>): String =
    downloadJson.encodeToString(PhotoPages(photoId = photoId, albumId = "", title = "", images = images))

/** 反序列化离线页列表；损坏时返回 null 走在线回退。 */
internal fun decodePhotoPages(json: String): PhotoPages? = runCatching {
    downloadJson.decodeFromString(PhotoPages.serializer(), json)
}.getOrNull()
