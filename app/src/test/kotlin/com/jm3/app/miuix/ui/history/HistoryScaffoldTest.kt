package com.jm3.app.miuix.ui.history

import com.jm3.core.history.HistoryCursor
import com.jm3.core.history.HistoryEntry
import com.jm3.core.history.HistoryRepository
import com.jm3.core.history.InMemoryHistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 阅读历史共享骨架：装载、续页、跳页、删除撤销、清空的编排。
 *
 * 断言只落在业务输出上（列表条目与顺序、页码、提示文案、仓库真实收到的请求），仓库用真实的内存实现，
 * 只有失败注入与「迟到响应」的闸门打在仓库边界上。协程作用域走 Unconfined，让内存实现同步跑完，
 * 用例之间不共享任何状态。
 */
class HistoryScaffoldTest {

    private fun entry(
        photoId: String,
        albumId: String = "a1",
        updatedAt: Long = 1000L,
    ) = HistoryEntry(
        photoId = photoId,
        albumId = albumId,
        title = "title-$photoId",
        coverUrl = null,
        updatedAt = updatedAt,
    )

    /** 按最近阅读时间倒序的第 n 条：p1 最新，p20 的 updatedAt 是 980。 */
    private fun entries(count: Int, albumId: String = "a1"): List<HistoryEntry> =
        (1..count).map { index -> entry(photoId = "p$index", albumId = albumId, updatedAt = 1000L - index) }

    private class Prompts {
        val messages = mutableListOf<String>()
        var undoAnswer = false
        var undoPrompts = 0
        var scrollTops = 0
    }

    private inner class Fixture(
        handling: HistoryResponseHandling = HistoryResponseHandling.DropLateResponses,
    ) {
        val repository = StubHistoryRepository()
        val prompts = Prompts()
        val state = HistoryScaffoldState(
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined),
            responseHandling = handling,
            showMessage = { message -> prompts.messages += message },
            showUndoPrompt = {
                prompts.undoPrompts += 1
                prompts.undoAnswer
            },
            scrollToTop = { prompts.scrollTops += 1 },
        )

        fun seed(vararg items: HistoryEntry) {
            runBlocking { repository.seed(items.toList()) }
        }
    }

    /** 仓库边界：真实切片交给内存实现，额外记录调用并允许按操作注入失败或挂起。 */
    private class StubHistoryRepository : HistoryRepository {
        private var clockValue = 0L
        private val delegate = InMemoryHistoryRepository(clock = { clockValue })

        val pageCalls = mutableListOf<Int>()
        val pageAfterCalls = mutableListOf<HistoryCursor?>()
        val upserted = mutableListOf<String>()
        val deletedAlbums = mutableListOf<String>()
        var clearCalls = 0
        var failPage = false
        var failPageAfter = false
        var failDelete = false
        var failUpsert = false
        var failClear = false

        /** 覆盖 page 的返回值；null 时按真实存储切片。 */
        var pageAnswer: List<HistoryEntry>? = null

        /** 第几次 page 调用在返回前挂起，配合 [pageGate] 模拟迟到响应。 */
        var gatePageCall: Int? = null
        var pageGate: CompletableDeferred<Unit>? = null

        suspend fun seed(items: List<HistoryEntry>) {
            items.forEach { item ->
                clockValue = item.updatedAt
                delegate.upsert(item)
            }
        }

        override suspend fun upsert(entry: HistoryEntry) {
            if (failUpsert) throw IllegalStateException("upsert failed")
            upserted += entry.photoId
            clockValue = entry.updatedAt
            delegate.upsert(entry)
        }

        override suspend fun page(page: Int, pageSize: Int): List<HistoryEntry> {
            pageCalls += page
            val answer = pageAnswer ?: delegate.page(page, pageSize)
            if (gatePageCall == pageCalls.size) {
                pageGate?.await()
            }
            if (failPage) throw IllegalStateException("page failed")
            return answer
        }

        override suspend fun pageAfter(cursor: HistoryCursor?, pageSize: Int): List<HistoryEntry> {
            pageAfterCalls += cursor
            if (failPageAfter) throw IllegalStateException("pageAfter failed")
            return delegate.pageAfter(cursor, pageSize)
        }

        override suspend fun latestForAlbum(albumId: String): HistoryEntry? = delegate.latestForAlbum(albumId)

        override suspend fun readPhotoIds(albumId: String): Set<String> = delegate.readPhotoIds(albumId)

        override suspend fun delete(photoId: String) {
            delegate.delete(photoId)
        }

        override suspend fun deleteAlbum(albumId: String) {
            deletedAlbums += albumId
            if (failDelete) throw IllegalStateException("delete failed")
            delegate.deleteAlbum(albumId)
        }

        override suspend fun clear() {
            clearCalls += 1
            if (failClear) throw IllegalStateException("clear failed")
            delegate.clear()
        }
    }

    @Test
    fun 初始态是加载中且列表为空() {
        val fixture = Fixture()

        assertEquals(HistoryListState.Loading, fixture.state.state)
        assertTrue(fixture.state.feed.items.isEmpty())
        assertFalse(fixture.state.pageBusy)
        assertFalse(fixture.state.clearing)
        assertFalse(fixture.state.showClearConfirm)
        assertTrue(fixture.repository.pageCalls.isEmpty())
    }

    @Test
    fun 首屏成功写入第一页并进入内容态() {
        val fixture = Fixture()
        fixture.seed(entry("p1", updatedAt = 30), entry("p2", updatedAt = 20))

        fixture.state.loadFirstPage()

        assertEquals(HistoryListState.Loaded, fixture.state.state)
        assertEquals(listOf("p1", "p2"), fixture.state.feed.items.map { it.photoId })
        assertEquals(1, fixture.state.feed.page)
        assertFalse(fixture.state.feed.hasMore)
        assertEquals(listOf(1), fixture.repository.pageCalls)
    }

    @Test
    fun 满页时标记还有下一页() {
        val fixture = Fixture()
        fixture.seed(*entries(20).toTypedArray())

        fixture.state.loadFirstPage()

        assertEquals(20, fixture.state.feed.items.size)
        assertTrue(fixture.state.feed.hasMore)
    }

    @Test
    fun 首屏失败进入错误态并给出兜底文案() {
        val fixture = Fixture()
        fixture.repository.failPage = true

        fixture.state.loadFirstPage()

        assertEquals(HistoryListState.Failed("历史加载失败"), fixture.state.state)
        assertTrue(fixture.state.feed.items.isEmpty())
    }

    @Test
    fun 重试从错误态重新装载() {
        val fixture = Fixture()
        fixture.repository.failPage = true
        fixture.state.loadFirstPage()
        fixture.seed(entry("p1", updatedAt = 10))

        fixture.repository.failPage = false
        fixture.state.loadFirstPage()

        assertEquals(HistoryListState.Loaded, fixture.state.state)
        assertEquals(listOf("p1"), fixture.state.feed.items.map { it.photoId })
        assertEquals(listOf(1, 1), fixture.repository.pageCalls)
    }

    @Test
    fun 续页走游标而跳页走页码() {
        val fixture = Fixture()
        fixture.seed(*entries(45).toTypedArray())
        fixture.state.loadFirstPage()

        fixture.state.loadMore()

        // 续页把上一页最后一条当作游标，绝不回头用页码算 OFFSET
        assertEquals(listOf<HistoryCursor?>(HistoryCursor(updatedAt = 980L, photoId = "p20")), fixture.repository.pageAfterCalls)
        assertEquals(listOf(1), fixture.repository.pageCalls)
        assertEquals(2, fixture.state.feed.page)
        assertEquals(40, fixture.state.feed.items.size)

        fixture.state.jumpToPage(1)

        // 跳页交给 page()：多出来的只有页码请求，游标请求一次都没多发
        assertEquals(listOf(1, 1), fixture.repository.pageCalls)
        assertEquals(1, fixture.repository.pageAfterCalls.size)
        assertEquals(1, fixture.state.feed.page)
        assertEquals("p1", fixture.state.feed.items.first().photoId)
        assertEquals(1, fixture.prompts.scrollTops)
    }

    @Test
    fun 跳页成功换到目标页并复位滚动位置() {
        val fixture = Fixture()
        fixture.seed(*entries(45).toTypedArray())
        fixture.state.loadFirstPage()
        fixture.state.loadMore()

        fixture.state.jumpToPage(3)

        assertEquals(listOf(1, 3), fixture.repository.pageCalls)
        assertEquals(3, fixture.state.feed.page)
        assertEquals(
            listOf("p41", "p42", "p43", "p44", "p45"),
            fixture.state.feed.items.map { it.photoId },
        )
        assertEquals(1, fixture.prompts.scrollTops)
    }

    @Test
    fun 跳页失败进入错误态() {
        val fixture = Fixture()
        fixture.seed(*entries(45).toTypedArray())
        fixture.state.loadFirstPage()
        fixture.repository.failPage = true

        fixture.state.jumpToPage(2)

        assertEquals(HistoryListState.Failed("历史加载失败"), fixture.state.state)
        assertFalse(fixture.state.pageBusy)
    }

    @Test
    fun 跳页在途时拒绝重复请求() {
        val fixture = Fixture()
        fixture.seed(*entries(45).toTypedArray())
        fixture.state.loadFirstPage()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.gatePageCall = 2
        fixture.repository.pageGate = gate

        fixture.state.jumpToPage(2)
        assertTrue(fixture.state.pageBusy)
        fixture.state.jumpToPage(4)

        gate.complete(Unit)

        assertEquals(listOf(1, 2), fixture.repository.pageCalls)
        assertFalse(fixture.state.pageBusy)
        assertEquals(2, fixture.state.feed.page)
    }

    @Test
    fun 续页失败保留已加载条目并给出重试入口() {
        val fixture = Fixture()
        fixture.seed(*entries(45).toTypedArray())
        fixture.state.loadFirstPage()
        fixture.repository.failPageAfter = true

        fixture.state.loadMore()

        assertEquals(20, fixture.state.feed.items.size)
        assertTrue(fixture.state.feed.loadMoreFailed)
        assertFalse(fixture.state.feed.loadingMore)
        assertEquals(HistoryListState.Loaded, fixture.state.state)
    }

    @Test
    fun 未进入内容态时不发续页请求() {
        val fixture = Fixture()

        fixture.state.loadMore()

        assertTrue(fixture.repository.pageAfterCalls.isEmpty())
        assertTrue(fixture.state.feed.items.isEmpty())
    }

    @Test
    fun 删除作品成功从列表移除并等待撤销() {
        val fixture = Fixture()
        fixture.seed(entry("p1", "a1", 30), entry("p2", "a2", 20))
        fixture.state.loadFirstPage()

        fixture.state.removeAlbum("a1")

        assertEquals(listOf("p2"), fixture.state.feed.items.map { it.photoId })
        assertEquals(listOf("a1"), fixture.repository.deletedAlbums)
        assertEquals(1, fixture.prompts.undoPrompts)
        assertTrue(fixture.prompts.messages.isEmpty())
    }

    @Test
    fun 删除失败保留列表并提示删除失败() {
        val fixture = Fixture()
        fixture.seed(entry("p1", "a1", 30), entry("p2", "a2", 20))
        fixture.state.loadFirstPage()
        fixture.repository.failDelete = true

        fixture.state.removeAlbum("a1")

        assertEquals(listOf("p1", "p2"), fixture.state.feed.items.map { it.photoId })
        assertEquals(listOf("删除失败"), fixture.prompts.messages)
        assertEquals(0, fixture.prompts.undoPrompts)
    }

    @Test
    fun 撤销把整部记录放回原位置() {
        val fixture = Fixture()
        fixture.seed(entry("p1", "a1", 30), entry("p2", "a2", 20), entry("p3", "a3", 10))
        fixture.state.loadFirstPage()
        fixture.prompts.undoAnswer = true

        fixture.state.removeAlbum("a2")

        assertEquals(listOf("p1", "p2", "p3"), fixture.state.feed.items.map { it.photoId })
        assertEquals(listOf("p2"), fixture.repository.upserted)
        assertEquals(1, fixture.prompts.undoPrompts)
    }

    @Test
    fun 撤销失败提示撤销失败并保持已删除状态() {
        val fixture = Fixture()
        fixture.seed(entry("p1", "a1", 30), entry("p2", "a2", 20), entry("p3", "a3", 10))
        fixture.state.loadFirstPage()
        fixture.prompts.undoAnswer = true
        fixture.repository.failUpsert = true

        fixture.state.removeAlbum("a2")

        assertEquals(listOf("p1", "p3"), fixture.state.feed.items.map { it.photoId })
        assertEquals(listOf("撤销失败"), fixture.prompts.messages)
    }

    @Test
    fun 清空成功回到空内容态并关掉确认框() {
        val fixture = Fixture()
        fixture.seed(entry("p1", updatedAt = 30), entry("p2", updatedAt = 20))
        fixture.state.loadFirstPage()
        fixture.state.requestClearConfirm()
        assertTrue(fixture.state.showClearConfirm)

        fixture.state.clearAll()

        assertTrue(fixture.state.feed.items.isEmpty())
        assertEquals(HistoryListState.Loaded, fixture.state.state)
        assertFalse(fixture.state.showClearConfirm)
        assertFalse(fixture.state.clearing)
        assertEquals(1, fixture.repository.clearCalls)
    }

    @Test
    fun 守卫模式下清空失败保留列表并提示清除失败() {
        val fixture = Fixture()
        fixture.seed(entry("p1", updatedAt = 30), entry("p2", updatedAt = 20))
        fixture.state.loadFirstPage()
        fixture.repository.failClear = true

        fixture.state.clearAll()

        assertEquals(listOf("p1", "p2"), fixture.state.feed.items.map { it.photoId })
        assertEquals(HistoryListState.Loaded, fixture.state.state)
        assertEquals(listOf("清除失败"), fixture.prompts.messages)
    }

    @Test
    fun 无守卫模式下清空失败同样回到空内容态() {
        val fixture = Fixture(HistoryResponseHandling.ApplyAsRequested)
        fixture.seed(entry("p1", updatedAt = 30), entry("p2", updatedAt = 20))
        fixture.state.loadFirstPage()
        fixture.repository.failClear = true

        fixture.state.clearAll()

        assertTrue(fixture.state.feed.items.isEmpty())
        assertEquals(HistoryListState.Loaded, fixture.state.state)
        assertTrue(fixture.prompts.messages.isEmpty())
    }

    @Test
    fun 守卫模式下迟到响应不覆盖后一次装载() {
        val fixture = Fixture()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.gatePageCall = 1
        fixture.repository.pageGate = gate
        fixture.repository.pageAnswer = listOf(entry("old1", updatedAt = 10))

        fixture.state.loadFirstPage()
        fixture.repository.pageAnswer = listOf(entry("new1", updatedAt = 20))
        fixture.state.loadFirstPage()
        gate.complete(Unit)

        assertEquals(listOf("new1"), fixture.state.feed.items.map { it.photoId })
    }

    @Test
    fun 无守卫模式下迟到响应照常落点() {
        val fixture = Fixture(HistoryResponseHandling.ApplyAsRequested)
        val gate = CompletableDeferred<Unit>()
        fixture.repository.gatePageCall = 1
        fixture.repository.pageGate = gate
        fixture.repository.pageAnswer = listOf(entry("old1", updatedAt = 10))

        fixture.state.loadFirstPage()
        fixture.repository.pageAnswer = listOf(entry("new1", updatedAt = 20))
        fixture.state.loadFirstPage()
        gate.complete(Unit)

        assertEquals(listOf("old1"), fixture.state.feed.items.map { it.photoId })
    }
}
