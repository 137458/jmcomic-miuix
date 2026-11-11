package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.NovelChapterMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouiReaderLogicTest {

    private fun chapter(id: String, needBuy: Boolean = false) = NovelChapterMeta(
        ncid = id,
        title = "第$id 章",
        sort = id,
        isNew = false,
        purchased = !needBuy,
        needBuy = needBuy,
    )

    @Test
    fun 弹窗切换互斥且打开无弹窗等价关闭() {
        val comments = CouiReaderModalState().open(CouiReaderModal.COMMENTS)
        assertEquals(CouiReaderModal.COMMENTS, couiVisibleReaderModal(comments, hasReplyTarget = false))
        assertTrue(couiHasOpenDialog(comments, hasReplyTarget = false))

        val switched = comments.open(CouiReaderModal.CHAPTERS)
        assertEquals(CouiReaderModal.CHAPTERS, couiVisibleReaderModal(switched, hasReplyTarget = false))

        val closed = switched.open(CouiReaderModal.NONE)
        assertNull(couiVisibleReaderModal(closed, hasReplyTarget = false))
        assertFalse(couiHasOpenDialog(closed, hasReplyTarget = false))
    }

    @Test
    fun 进入回复让出评论弹窗且完成后恢复() {
        val comments = CouiReaderModalState().open(CouiReaderModal.COMMENTS)
        val replying = comments.enterReply()
        assertNull(couiVisibleReaderModal(replying, hasReplyTarget = true))
        assertTrue(replying.reopenCommentsAfterReply)
        assertTrue(couiHasOpenDialog(replying, hasReplyTarget = true))

        val backToComments = replying.exitReply()
        assertEquals(CouiReaderModal.COMMENTS, couiVisibleReaderModal(backToComments, hasReplyTarget = false))
        assertFalse(backToComments.reopenCommentsAfterReply)
    }

    @Test
    fun 跳转登录清空所有弹窗与回复回退标志() {
        val replying = CouiReaderModalState()
            .open(CouiReaderModal.COMMENTS)
            .enterReply()
        val afterLoginRedirect = replying.dismissAllForNavigation()
        assertEquals(CouiReaderModal.NONE, afterLoginRedirect.activeModal)
        assertFalse(afterLoginRedirect.reopenCommentsAfterReply)
        assertNull(couiVisibleReaderModal(afterLoginRedirect, hasReplyTarget = false))
        assertFalse(couiHasOpenDialog(afterLoginRedirect, hasReplyTarget = false))
    }

    @Test
    fun 非评论弹窗切进回复后再退出不会误开评论() {
        val plainReply = CouiReaderModalState().open(CouiReaderModal.PAGE_JUMP).enterReply()
        val finished = plainReply.exitReply()
        assertNull(couiVisibleReaderModal(finished, hasReplyTarget = false))
        assertFalse(finished.reopenCommentsAfterReply)
    }

    @Test
    fun 纵向页宽扣减左右可读区留白并夹取下限() {
        assertEquals(1080, couiReaderVerticalPageWidthPx(windowWidthPx = 1080, readableHorizontalPaddingPx = 0))
        assertEquals(1680, couiReaderVerticalPageWidthPx(windowWidthPx = 2560, readableHorizontalPaddingPx = 440))
        assertEquals(1, couiReaderVerticalPageWidthPx(windowWidthPx = 400, readableHorizontalPaddingPx = 300))
        assertEquals(1, couiReaderVerticalPageWidthPx(windowWidthPx = 0, readableHorizontalPaddingPx = 0))
    }

    @Test
    fun 提示条底边距随HUD显隐切换() {
        assertEquals(80, couiReaderSnackbarBottomPaddingDp(hudVisible = true, bottomBarHeightDp = 64, barVerticalMarginDp = 8))
        assertEquals(0, couiReaderSnackbarBottomPaddingDp(hudVisible = false, bottomBarHeightDp = 64, barVerticalMarginDp = 8))
        assertEquals(0, couiReaderSnackbarBottomPaddingDp(hudVisible = true, bottomBarHeightDp = -10, barVerticalMarginDp = -4))
    }

    @Test
    fun 评论粘贴超长输入按上限截断而非整段丢弃() {
        val raw = "a".repeat(250)
        val clamped = couiClampCommentInput(raw)
        assertEquals(200, clamped.length)
        assertEquals("a".repeat(200), clamped)
        assertEquals("短评", couiClampCommentInput("短评"))
        assertEquals("", couiClampCommentInput("abc", maxLength = 0))
    }

    @Test
    fun 跳页步进按起止页范围安全夹取() {
        assertEquals(1, couiStepJumpPage(currentPage = 1, delta = -1, totalPages = 50))
        assertEquals(12, couiStepJumpPage(currentPage = 13, delta = -1, totalPages = 50))
        assertEquals(50, couiStepJumpPage(currentPage = 50, delta = 1, totalPages = 50))
        assertEquals(1, couiStepJumpPage(currentPage = 3, delta = -5, totalPages = 0))
    }

    @Test
    fun 横向模式同步只在加载恢复完成且无跳页动画时触发() {
        assertFalse(
            couiShouldSyncPagerOnModeChange(
                horizontalReady = false,
                progressRestored = true,
                horizontal = true,
                pagerPageCount = 10,
                pendingJumpTarget = null,
            ),
        )
        assertFalse(
            couiShouldSyncPagerOnModeChange(
                horizontalReady = true,
                progressRestored = false,
                horizontal = true,
                pagerPageCount = 10,
                pendingJumpTarget = null,
            ),
        )
        assertFalse(
            couiShouldSyncPagerOnModeChange(
                horizontalReady = true,
                progressRestored = true,
                horizontal = true,
                pagerPageCount = 10,
                pendingJumpTarget = 12,
            ),
        )
        assertFalse(
            couiShouldSyncPagerOnModeChange(
                horizontalReady = true,
                progressRestored = true,
                horizontal = false,
                pagerPageCount = 10,
                pendingJumpTarget = null,
            ),
        )
        assertFalse(
            couiShouldSyncPagerOnModeChange(
                horizontalReady = true,
                progressRestored = true,
                horizontal = true,
                pagerPageCount = 0,
                pendingJumpTarget = null,
            ),
        )
        assertTrue(
            couiShouldSyncPagerOnModeChange(
                horizontalReady = true,
                progressRestored = true,
                horizontal = true,
                pagerPageCount = 10,
                pendingJumpTarget = null,
            ),
        )
    }

    @Test
    fun 小说相邻章节计算排除未命中章与越界() {
        val list = listOf(chapter("1"), chapter("2"), chapter("3"))
        assertEquals(null to list[1], couiAdjacentNovelChapters(list, "1"))
        assertEquals(list[0] to list[2], couiAdjacentNovelChapters(list, "2"))
        assertEquals(list[1] to null, couiAdjacentNovelChapters(list, "3"))
        assertEquals(null to null, couiAdjacentNovelChapters(list, "missing"))
        assertEquals(null to null, couiAdjacentNovelChapters(emptyList(), "1"))
    }

    @Test
    fun 小说音量键滚动步进按视口百分之八十计算并夹取边界() {
        assertEquals(
            800,
            couiNovelVolumeScrollTarget(currentScrollPx = 0, maxScrollPx = 5000, viewportHeightPx = 1000, direction = 1),
        )
        assertEquals(
            5000,
            couiNovelVolumeScrollTarget(currentScrollPx = 4600, maxScrollPx = 5000, viewportHeightPx = 1000, direction = 1),
        )
        assertEquals(
            0,
            couiNovelVolumeScrollTarget(currentScrollPx = 200, maxScrollPx = 5000, viewportHeightPx = 1000, direction = -1),
        )
        assertEquals(
            1200,
            couiNovelVolumeScrollTarget(currentScrollPx = 2000, maxScrollPx = 5000, viewportHeightPx = 1000, direction = -1),
        )
    }

    @Test
    fun 音量键翻页在浮层或弹窗打开时让出控制权() {
        assertTrue(couiShouldHandleReaderVolumeKey(delta = 1, hasZoomOverlay = false, hasOpenModal = false))
        assertFalse(couiShouldHandleReaderVolumeKey(delta = 0, hasZoomOverlay = false, hasOpenModal = false))
        assertFalse(couiShouldHandleReaderVolumeKey(delta = 1, hasZoomOverlay = true, hasOpenModal = false))
        assertFalse(couiShouldHandleReaderVolumeKey(delta = -1, hasZoomOverlay = false, hasOpenModal = true))
    }

    private fun couiHasOpenDialog(state: CouiReaderModalState, hasReplyTarget: Boolean): Boolean =
        couiHasOpenReaderModal(state, hasReplyTarget)

    @Test
    fun 阅读器HUD阴影按顶底位置区分并关闭顶栏向下焦散溢光() {
        val topContourDark = couiReaderHudShadowSpec(
            placement = CouiReaderHudPlacement.TOP,
            dark = true,
            contourGlowEnabled = true,
        )
        assertEquals(0f, topContourDark.causticBottomDp)
        assertEquals(0f, topContourDark.causticSdfDp)
        assertEquals(0f, topContourDark.causticAlpha)
        assertEquals(16f, topContourDark.elevationDp)
        assertTrue(topContourDark.smoothCorner)

        val topPlainLight = couiReaderHudShadowSpec(
            placement = CouiReaderHudPlacement.TOP,
            dark = false,
            contourGlowEnabled = false,
        )
        assertEquals(0f, topPlainLight.causticAlpha)
        assertEquals(100f, topPlainLight.elevationDp)
        assertEquals(10000f, topPlainLight.lightZDp)
        assertTrue(topPlainLight.smoothCorner)

        val bottomContourDark = couiReaderHudShadowSpec(
            placement = CouiReaderHudPlacement.BOTTOM,
            dark = true,
            contourGlowEnabled = true,
        )
        assertEquals(4.9f, bottomContourDark.causticBottomDp)
        assertEquals(10.4f, bottomContourDark.causticSdfDp)
        assertEquals(0x42 / 255f, bottomContourDark.causticAlpha)
        assertEquals(24f, bottomContourDark.elevationDp)
        assertTrue(bottomContourDark.smoothCorner)

        val bottomPlainDark = couiReaderHudShadowSpec(
            placement = CouiReaderHudPlacement.BOTTOM,
            dark = false,
            contourGlowEnabled = false,
        )
        assertEquals(350f, bottomPlainDark.elevationDp)
        assertEquals(0x28 / 255f, bottomPlainDark.shadowAlpha)
        assertEquals(4.9f, bottomPlainDark.causticBottomDp)
        assertTrue(bottomPlainDark.smoothCorner)
    }
}
