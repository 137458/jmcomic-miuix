package com.jm3.app.miuix.ui

import com.jm3.core.download.DownloadStore
import com.jm3.core.download.PhotoSource
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.session.AuthCall
import com.jm3.core.session.StartupGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 阅读器共享 loader 的键与复用判定：只由网关与下载源决定。
 *
 * 回归点：详情页的 rememberReaderImageLoader 不传会话通道；若通道参与判等，每次进出详情页都会
 * 重建 loader、清空 reader_images 磁盘快照，反向切回阅读器时 remember 键未变，却拿着已关闭的实例。
 */
class ReaderLoaderKeyTest {

    private class FakePhotoSource : PhotoSource {
        override fun photo(photoId: String): PhotoPages = error("键判等测试不取图")

        override fun imageBytes(image: PageImage): ByteArray = error("键判等测试不取图")
    }

    private class FakeDownloadStore : DownloadStore {
        override fun exists(photoId: String, index: Int): Boolean = false

        override fun write(photoId: String, index: Int, bytes: ByteArray) = Unit

        override fun delete(photoId: String) = Unit

        override fun files(photoId: String): List<Int> = emptyList()

        override fun read(photoId: String, index: Int): ByteArray? = null
    }

    private class FakeGate : StartupGate {
        override suspend fun awaitReady() = Unit

        override suspend fun onSessionExpired() = Unit
    }

    @Test
    fun 同网关同下载源的键相等() {
        val gateway = FakePhotoSource()
        val store = FakeDownloadStore()

        assertEquals(readerLoaderKeyOf(gateway, store), readerLoaderKeyOf(gateway, store))
    }

    @Test
    fun 同网关同下载源复用已有实例() {
        val gateway = FakePhotoSource()
        val store = FakeDownloadStore()
        val held = readerLoaderKeyOf(gateway, store)

        assertTrue(shouldReuseReaderLoader(held, readerLoaderKeyOf(gateway, store)))
    }

    @Test
    fun 网关不同必须重建() {
        val store = FakeDownloadStore()
        val held = readerLoaderKeyOf(FakePhotoSource(), store)
        val next = readerLoaderKeyOf(FakePhotoSource(), store)

        assertNotEquals(held, next)
        assertFalse(shouldReuseReaderLoader(held, next))
    }

    @Test
    fun 下载源不同必须重建() {
        val gateway = FakePhotoSource()
        val held = readerLoaderKeyOf(gateway, FakeDownloadStore())

        assertNotEquals(held, readerLoaderKeyOf(gateway, FakeDownloadStore()))
        assertFalse(shouldReuseReaderLoader(held, readerLoaderKeyOf(gateway, null)))
    }

    @Test
    fun 没有已建实例时不复用() {
        assertFalse(shouldReuseReaderLoader(null, readerLoaderKeyOf(FakePhotoSource(), null)))
    }

    @Test
    fun 会话通道接线后读取到同一实例() {
        val channel = ReaderAuthChannel()
        val authCall = AuthCall(FakeGate())

        assertNull(channel.current())
        channel.wire(authCall)
        assertSame(authCall, channel.current())
    }

    @Test
    fun 后接线覆盖先接线() {
        val channel = ReaderAuthChannel()
        val first = AuthCall(FakeGate())
        val second = AuthCall(FakeGate())

        channel.wire(first)
        channel.wire(second)

        assertSame(second, channel.current())
    }

    @Test
    fun 省略通道的调用方不会清掉别处已接好的闸门() {
        val channel = ReaderAuthChannel()
        val authCall = AuthCall(FakeGate())

        channel.wire(authCall)
        // 详情页不传会话通道，落地时写的是 null
        channel.wire(null)

        assertSame(authCall, channel.current())
    }

    @Test
    fun 会话通道变化不触发共享实例重建() {
        val gateway = FakePhotoSource()
        val store = FakeDownloadStore()
        val held = readerLoaderKeyOf(gateway, store)
        val channel = ReaderAuthChannel()

        // 阅读器接线 → 进详情页（不接线）→ 回阅读器：键必须始终相等
        channel.wire(AuthCall(FakeGate()))
        assertTrue(shouldReuseReaderLoader(held, readerLoaderKeyOf(gateway, store)))
        channel.wire(AuthCall(FakeGate()))
        assertTrue(shouldReuseReaderLoader(held, readerLoaderKeyOf(gateway, store)))
    }
}
