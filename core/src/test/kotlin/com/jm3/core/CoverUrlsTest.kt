package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 封面地址纯构造器：语义与库 AbstractJmClient.getAlbumCoverUrl 三参重载一致。 */
class CoverUrlsTest {

    @Test
    fun 常规域名与尺寸_拼接为完整封面地址() {
        assertEquals(
            "https://cdn-msp.example.xyz/media/albums/123456_3x4.jpg",
            CoverUrls.build(albumId = "123456", imageDomain = "cdn-msp.example.xyz", size = "_3x4"),
        )
    }

    @Test
    fun 域名已含协议_不重复拼接() {
        assertEquals(
            "https://cdn.example.com/media/albums/42.jpg",
            CoverUrls.build(albumId = "42", imageDomain = "https://cdn.example.com"),
        )
    }

    @Test
    fun 空相册id_返回null() {
        assertNull(CoverUrls.build(albumId = "", imageDomain = "cdn.example.com"))
    }

    @Test
    fun 空域名_返回null() {
        assertNull(CoverUrls.build(albumId = "42", imageDomain = ""))
    }
}
