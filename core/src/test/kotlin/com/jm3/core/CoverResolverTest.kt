package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverResolverTest {

    private val ready = CoverResolver.of("18comic.vip")

    @Test
    fun resolve域名就绪给出可加载地址() {
        assertEquals(
            Cover.Ready("https://18comic.vip/media/albums/123456_3x4.jpg"),
            ready.resolve("123456"),
        )
    }

    @Test
    fun resolve尺寸变体进入地址() {
        assertEquals(
            Cover.Ready("https://18comic.vip/media/albums/123456_1x1.jpg"),
            ready.resolve("123456", "_1x1"),
        )
    }

    @Test
    fun resolve域名未就绪是Unready而不是None() {
        assertEquals(Cover.Unready, CoverResolver.of(null as String?).resolve("123456"))
        assertEquals(Cover.Unready, CoverResolver.of("   ").resolve("123456"))
        assertEquals(Cover.Unready, CoverResolver.Unready.resolve("123456"))
    }

    @Test
    fun resolve车号为空是None而不是Unready() {
        assertEquals(Cover.None, ready.resolve(null))
        assertEquals(Cover.None, ready.resolve("   "))
        // 车号与域名同时缺失：车号侧不可重试，所以是 None
        assertEquals(Cover.None, CoverResolver.of(null as String?).resolve(""))
    }

    @Test
    fun resolve车号去空白后拼地址() {
        assertEquals(
            Cover.Ready("https://18comic.vip/media/albums/42_3x4.jpg"),
            ready.resolve("  42  "),
        )
    }

    @Test
    fun resolve域名带协议与路径时先归一() {
        assertEquals(
            Cover.Ready("https://18comic.vip/media/albums/7_3x4.jpg"),
            CoverResolver.of("https://18comic.vip/some/path/").resolve("7"),
        )
    }

    @Test
    fun urlOrNull只在Ready时给出地址() {
        assertEquals("https://18comic.vip/media/albums/123456_3x4.jpg", ready.resolve("123456").urlOrNull)
        assertNull(Cover.Unready.urlOrNull)
        assertNull(Cover.None.urlOrNull)
    }

    @Test
    fun image相对路径拼域名绝对地址原样() {
        assertEquals("https://18comic.vip/media/photos/1.jpg", ready.image("media/photos/1.jpg"))
        assertEquals("https://18comic.vip/media/photos/1.jpg", ready.image("/media/photos/1.jpg"))
        assertEquals("https://cdn.example/x.jpg", ready.image("https://cdn.example/x.jpg"))
        assertEquals("http://cdn.example/x.jpg", ready.image("http://cdn.example/x.jpg"))
    }

    @Test
    fun image域名未就绪时相对路径给null绝对地址仍可用() {
        assertNull(CoverResolver.Unready.image("media/photos/1.jpg"))
        assertEquals("https://cdn.example/x.jpg", CoverResolver.Unready.image("https://cdn.example/x.jpg"))
        assertNull(ready.image("   "))
        assertNull(ready.image(null))
    }

    @Test
    fun coverCacheKeyText与resolve互逆() {
        for (id in listOf("1", "123456", "1487051")) {
            val cover = ready.resolve(id)
            assertTrue(cover is Cover.Ready, "车号 $id 应可加载，实际是 $cover")
            assertEquals("jm3-album-cover:$id", coverCacheKeyText(cover.urlOrNull))
        }
    }

    @Test
    fun coverCacheKeyText按车号归并线路漂移的同一作品() {
        val a = coverCacheKeyText("https://www.cdnhjk.net/media/albums/1487051_3x4.jpg")
        val b = coverCacheKeyText("https://www.cdngwc.net/media/albums/1487051_3x4.jpg")
        assertEquals("jm3-album-cover:1487051", a)
        assertEquals(a, b)
    }

    @Test
    fun coverCacheKeyText不把非作品封面地址当成车号() {
        assertEquals("https://example.com/cover.jpg", coverCacheKeyText("https://example.com/cover.jpg"))
        assertEquals(
            "https://example.com/media/albums/not-a-number.jpg",
            coverCacheKeyText("https://example.com/media/albums/not-a-number.jpg"),
        )
        assertNull(coverCacheKeyText(null))
        assertNull(coverCacheKeyText("   "))
    }

    @Test
    fun coverCacheKeyText非作品封面原样去空白() {
        assertEquals("https://example.com/cover.jpg", coverCacheKeyText("  https://example.com/cover.jpg  "))
        assertNull(coverCacheKeyText("   "))
    }
}