package com.jm3.core

/** 封面地址纯构造器；与库 AbstractJmClient.getAlbumCoverUrl(albumId, imageDomain, size) 语义一致。 */
object CoverUrls {
    private const val HTTPS = "https://"

    fun build(albumId: String, imageDomain: String, size: String = ""): String? {
        if (albumId.isBlank() || imageDomain.isBlank()) return null
        val base = if (imageDomain.startsWith(HTTPS)) imageDomain else HTTPS + imageDomain
        return "$base/media/albums/$albumId$size.jpg"
    }
}
