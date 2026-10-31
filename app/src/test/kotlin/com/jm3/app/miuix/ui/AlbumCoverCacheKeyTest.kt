package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 封面缓存键的规范化：同一车号在不同线路下的 URL 必须归一到同一个键。
 *
 * 卡片的封面 URL 用列表首条的图片域名拼，详情页的用该作品自己的域名拼；两者不一致时
 * 缓存键不同 → 详情头图重新解码 → 用户看到"封面闪一下"。规范化键让同一车号只有一个条目。
 */
class AlbumCoverCacheKeyTest {

    @Test
    fun 同一车号的不同线路地址归一到同一个缓存键() {
        val keyA = albumCoverCacheKeyText("https://www.cdnhjk.net/media/albums/1487051_3x4.jpg")
        val keyB = albumCoverCacheKeyText("https://www.cdngwc.net/media/albums/1487051_3x4.jpg")

        assertEquals(keyA, keyB)
        assertEquals("jm3-album-cover:1487051", keyA)
    }

    @Test
    fun 不同车号得到不同缓存键() {
        val keyA = albumCoverCacheKeyText("https://www.cdnhjk.net/media/albums/1487051_3x4.jpg")
        val keyB = albumCoverCacheKeyText("https://www.cdnhjk.net/media/albums/1487052_3x4.jpg")

        assertNotEquals(keyA, keyB)
        assertEquals("jm3-album-cover:1487052", keyB)
    }

    @Test
    fun 非作品封面地址原样作为缓存键() {
        assertEquals(
            "https://example.com/cover.jpg",
            albumCoverCacheKeyText("https://example.com/cover.jpg"),
        )
        assertEquals(
            "https://example.com/media/albums/not-a-number.jpg",
            albumCoverCacheKeyText("  https://example.com/media/albums/not-a-number.jpg  "),
        )
    }

    @Test
    fun 空白地址没有缓存键() {
        assertNull(albumCoverCacheKeyText(null))
        assertNull(albumCoverCacheKeyText(""))
        assertNull(albumCoverCacheKeyText("   "))
    }

    @Test
    fun 缓存键与解码尺寸一致_同一车号归并后仍按固定尺寸() {
        assertEquals(IntSize(144, 192), heroCoverSizePx(1f))
        assertEquals(IntSize(288, 384), heroCoverSizePx(2f))
    }
}
