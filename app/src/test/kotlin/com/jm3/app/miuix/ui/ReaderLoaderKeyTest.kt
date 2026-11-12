package com.jm3.app.miuix.ui

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.PhotoSource
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.StartupGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun 在线取图失败带失效文案且无异常栈时识别为登录失效() {
        assertEquals(
            LOGIN_EXPIRED_MESSAGE,
            readerRemoteFailureMessage(SessionOutcome.Failed("""{"code":401}""", cause = null)),
        )
        assertEquals(
            LOGIN_EXPIRED_MESSAGE,
            readerRemoteFailureMessage(SessionOutcome.Failed("請先登入會員", cause = null)),
        )
        assertEquals(
            "网络超时，请稍后重试",
            readerRemoteFailureMessage(SessionOutcome.Failed("timeout", cause = java.net.SocketTimeoutException("timed out"))),
        )
    }

    @Test
    fun 同键并发请求按单飞串行并在结束后清理锁表() = runTest {
        val pool = ReaderKeyLockPool()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        val networkCalls = AtomicInteger(0)

        coroutineScope {
            val first = async {
                pool.withKey("k1") {
                    entered.complete(Unit)
                    release.await()
                    networkCalls.incrementAndGet()
                    cache["k1"] = "disk-snapshot"
                    cache.getValue("k1")
                }
            }
            entered.await()
            val second = async {
                pool.withKey("k1") {
                    cache["k1"] ?: run {
                        networkCalls.incrementAndGet()
                        "fallback"
                    }
                }
            }
            release.complete(Unit)
            assertEquals("disk-snapshot", first.await())
            assertEquals("disk-snapshot", second.await())
        }

        assertEquals(1, networkCalls.get(), "同键并发应由首个请求落盘后，竞争方直接复用快照")
        assertEquals(0, pool.activeKeyCount(), "所有同键请求完成后应清理锁表不泄漏")
    }
}
