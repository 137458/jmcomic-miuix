package com.jm3.app.miuix.data.download

import com.jm3.core.download.PhotoSource
import com.jm3.core.download.ResolvedChapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 接缝穿透测试：假 [PhotoSource] 只有两个方法，却能完整驱动 [GatewayPageFetcher] 与 [GatewayTaskResolver]。
 *
 * 这不是"给测试开后门"——生产侧传的是 Jm3Gateway（它继承 PhotoSource，Jm3Gateway.kt:46），
 * 测试侧传两方法的假实现，两边穿过同一个 seam。顺带钉住三条业务规则：
 * 页号 1 起经 PageNumbers 换算（GatewaySources.kt:31）、页列表会话内只拉一次（:18-:21）、
 * 越界页号带 photoId 与页号报错（:32）。
 */
class GatewaySourcesTest {

    private class FakePhotoSource(
        private val onPhoto: (String) -> PhotoPages,
        private val onBytes: (PageImage) -> ByteArray = { it.url.encodeToByteArray() },
    ) : PhotoSource {

        var photoCalls = 0
            private set

        var imageCalls = 0
            private set

        override fun photo(photoId: String): PhotoPages {
            photoCalls++
            return onPhoto(photoId)
        }

        override fun imageBytes(image: PageImage): ByteArray {
            imageCalls++
            return onBytes(image)
        }
    }

    private fun pages(photoId: String, count: Int = 3): PhotoPages = PhotoPages(
        photoId = photoId,
        albumId = "album-7",
        title = "第 1 话",
        images = (0 until count).map { index ->
            PageImage(
                photoId = photoId,
                scrambleId = "s$index",
                index = index,
                filename = "0000$index.jpg",
                url = "https://img.example/photos/$photoId/0000$index.jpg",
            )
        },
    )

    @Test
    fun `一基页号换算到零基下标再取字节`() = runTest {
        val source = FakePhotoSource({ pages(it) })
        val fetcher = GatewayPageFetcher { source }

        assertContentEquals(
            "https://img.example/photos/p/00000.jpg".encodeToByteArray(),
            fetcher.fetch("p", 1),
        )
        assertContentEquals(
            "https://img.example/photos/p/00002.jpg".encodeToByteArray(),
            fetcher.fetch("p", 3),
        )
        assertEquals(2, source.imageCalls)
    }

    @Test
    fun `页列表会话内只拉一次`() = runTest {
        val source = FakePhotoSource({ pages(it) })
        val fetcher = GatewayPageFetcher { source }

        fetcher.pages("p")
        fetcher.fetch("p", 1)
        fetcher.fetch("p", 2)

        assertEquals(1, source.photoCalls, "重试与逐页取图不应重复拉元数据")
    }

    @Test
    fun `不同章节各拉一次页列表`() = runTest {
        val source = FakePhotoSource({ pages(it) })
        val fetcher = GatewayPageFetcher { source }

        fetcher.fetch("p", 1)
        fetcher.fetch("q", 1)

        assertEquals(2, source.photoCalls)
    }

    @Test
    fun `页号越界时带photoId与页号报错`() = runTest {
        val fetcher = GatewayPageFetcher { FakePhotoSource({ pages(it, count = 3) }) }

        val failure = assertFailsWith<IllegalStateException> { fetcher.fetch("p", 4) }

        assertEquals("页号越界：photoId=p 页号=4", failure.message)
    }

    @Test
    fun `页号下界越界同样报错`() = runTest {
        val fetcher = GatewayPageFetcher { FakePhotoSource({ pages(it, count = 3) }) }

        val failure = assertFailsWith<IllegalStateException> { fetcher.fetch("p", 0) }

        assertEquals("页号越界：photoId=p 页号=0", failure.message)
    }

    @Test
    fun `未拉取过的章节无法序列化且不触网`() {
        val fetcher = GatewayPageFetcher { error("不应访问网关") }

        assertNull(fetcher.pagesJsonOrNull("p"))
    }

    @Test
    fun `拉取过的章节给出可解码的页列表JSON`() = runTest {
        val fetcher = GatewayPageFetcher { FakePhotoSource({ pages(it) }) }
        fetcher.pages("p")

        val json = fetcher.pagesJsonOrNull("p")

        val decoded = decodePhotoPages(json.orEmpty())
        assertEquals("p", decoded?.photoId)
        assertEquals(3, decoded?.images?.size)
        assertEquals(pages("p").images, decoded?.images)
    }

    @Test
    fun `补齐章节元数据走同一份页清单`() = runTest {
        val source = FakePhotoSource({ pages(it, count = 5) })
        val resolver = GatewayTaskResolver { source }

        assertEquals(
            ResolvedChapter(albumId = "album-7", title = "第 1 话", total = 5),
            resolver.resolve("p"),
        )
    }

    @Test
    fun `evict与clearCache清理对应章节页列表缓存`() = runTest {
        val source = FakePhotoSource({ pages(it) })
        val fetcher = GatewayPageFetcher { source }

        fetcher.pages("p")
        fetcher.pages("q")
        assertEquals(2, source.photoCalls)

        fetcher.evict("p")
        assertNull(fetcher.pagesJsonOrNull("p"))
        assertEquals(true, fetcher.pagesJsonOrNull("q") != null)
        fetcher.pages("p")
        assertEquals(3, source.photoCalls, "evict 后再次读取应重新请求网关")

        fetcher.clearCache()
        assertNull(fetcher.pagesJsonOrNull("p"))
        assertNull(fetcher.pagesJsonOrNull("q"))
    }
}
