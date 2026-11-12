package com.jm3.app.miuix.reader

import com.jm3.core.reader.ReaderPageProgress
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderSessionTest {
    /** [ReaderSession.restore] 是挂起的：测试里同步跑完，断言才看得到闸门与结论。 */
    private fun ReaderSession.restoreNow(
        readSaved: () -> ReaderPageProgress?,
        startPage: Int?,
    ) = runBlocking { restore(readSaved = readSaved, startPage = startPage) }

    @Test
    fun `跳页期间可见页回写被拒绝且预取按目标页推进`() {
        val session = ReaderSession(pageCount = 10)

        val target = session.requestJump(5)

        assertEquals(5, target)
        assertTrue(session.isJumping)
        // 动画途经的中间页把 firstVisibleIndex 报到 1，页码仍停在目标页
        assertEquals(listOf(5, 6, 7), session.onVisibleIndex(firstVisibleIndex = 1, ahead = 3))
        assertEquals(5, session.currentPage)
        assertTrue(session.isJumping)
    }

    @Test
    fun `跳页落地后可见页回写恢复生效`() {
        val session = ReaderSession(pageCount = 10)
        session.requestJump(5)

        session.onJumpSettled(5)

        assertFalse(session.isJumping)
        session.onVisibleIndex(firstVisibleIndex = 2, ahead = 3)
        assertEquals(3, session.currentPage)
    }

    @Test
    fun `旧动画落地不会清掉新目标`() {
        val session = ReaderSession(pageCount = 10)
        session.requestJump(2)
        session.requestJump(8)

        session.onJumpSettled(2)

        assertTrue(session.isJumping)
        assertEquals(8, session.currentPage)

        session.onJumpSettled(8)
        assertFalse(session.isJumping)
        assertEquals(8, session.currentPage)
    }

    @Test
    fun `跳页目标按页数夹取`() {
        val session = ReaderSession(pageCount = 10)

        assertEquals(10, session.requestJump(999))
        session.onJumpSettled(10)
        assertEquals(1, session.requestJump(0))
    }

    @Test
    fun `预取账本去重且可在布局基线变化后重置`() {
        val session = ReaderSession(pageCount = 10)

        assertEquals(listOf(1, 2, 3), session.onVisibleIndex(firstVisibleIndex = 0, ahead = 3))
        assertEquals(emptyList(), session.onVisibleIndex(firstVisibleIndex = 0, ahead = 3))

        session.resetPrefetchLedger()
        assertEquals(listOf(1, 2, 3), session.onVisibleIndex(firstVisibleIndex = 0, ahead = 3))
    }

    @Test
    fun `空章不跳页不预取也不写进度`() {
        val session = ReaderSession(pageCount = 0)

        assertNull(session.requestJump(1))
        assertEquals(emptyList(), session.onVisibleIndex(firstVisibleIndex = 0, ahead = 3))
        assertEquals(0, session.currentPage)
        assertNull(session.progressToSave())
    }

    @Test
    fun `布局落定之前不读旧进度也不开写盘闸门`() {
        val session = ReaderSession(pageCount = 10)
        var read = false

        val outcome = session.restoreNow(readSaved = { read = true; ReaderPageProgress(4, 10) }, startPage = null)

        assertNull(outcome)
        assertFalse(read)
        assertFalse(session.canPersistProgress)
    }

    @Test
    fun `恢复时先读旧进度再开闸并结算页码`() {
        val session = ReaderSession(pageCount = 10)
        session.onLayoutReady()
        var gateOpenAtReadTime = true

        val outcome = session.restoreNow(
            readSaved = {
                gateOpenAtReadTime = session.canPersistProgress
                ReaderPageProgress(4, 10)
            },
            startPage = null,
        )

        assertEquals(ReaderPageProgress(4, 10), outcome?.saved)
        assertEquals(4, outcome?.targetPage)
        // 读旧值这一刻闸门必须还关着，否则首帧的第 1 页会先落盘
        assertFalse(gateOpenAtReadTime)
        assertTrue(session.canPersistProgress)
    }

    @Test
    fun `同一份会话只恢复一次`() {
        val session = ReaderSession(pageCount = 10)
        session.onLayoutReady()
        assertEquals(4, session.restoreNow({ ReaderPageProgress(4, 10) }, startPage = null)?.targetPage)

        assertNull(session.restoreNow({ ReaderPageProgress(7, 10) }, startPage = null))
        assertEquals(1, session.currentPage)
    }

    @Test
    fun `起始页优先于历史进度且越界时回退历史`() {
        val direct = ReaderSession(pageCount = 10)
        direct.onLayoutReady()
        assertEquals(7, direct.restoreNow({ ReaderPageProgress(4, 10) }, startPage = 7)?.targetPage)

        val fallback = ReaderSession(pageCount = 10)
        fallback.onLayoutReady()
        assertEquals(4, fallback.restoreNow({ ReaderPageProgress(4, 10) }, startPage = 99)?.targetPage)
    }

    @Test
    fun `没有历史进度时仍开写盘闸门`() {
        val session = ReaderSession(pageCount = 10)
        session.onLayoutReady()

        val outcome = session.restoreNow(readSaved = { null }, startPage = null)

        assertNull(outcome?.targetPage)
        assertTrue(session.canPersistProgress)
    }

    @Test
    fun `落盘进度取当前页与总页数`() {
        val session = ReaderSession(pageCount = 10)
        session.onLayoutReady()
        session.restoreNow({ null }, startPage = null)
        session.requestJump(7)
        session.onJumpSettled(7)

        assertEquals(ReaderPageProgress(7, 10), session.progressToSave())
    }

    @Test
    fun `章节加载代际在小说屏复用同一闸门`() {
        val session = ReaderSession.chapterOnly()

        val first = session.beginChapterLoad()
        assertTrue(session.isLatestChapterLoad(first))

        val second = session.beginChapterLoad()
        assertFalse(session.isLatestChapterLoad(first))
        assertTrue(session.isLatestChapterLoad(second))
    }
}
