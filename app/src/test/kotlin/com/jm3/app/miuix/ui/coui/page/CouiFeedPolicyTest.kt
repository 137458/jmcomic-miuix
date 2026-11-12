// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.paging.PagedFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页与浏览页共用的触底预取口径与名次换算。
 *
 * 这两处原来各写一份 dp 阈值与同构的 snapshotFlow，阈值不同（320dp / 240dp）导致同样的列表
 * 在两页表现不一致；阈值、dp→项数换算与名次公式收敛到这里后，跨页一致性由用例守着。
 */
class CouiFeedPolicyTest {

    @Test
    fun `提前量阈值取 320dp`() {
        assertEquals(320, COUI_LOAD_MORE_PREFETCH_DP)
    }

    @Test
    fun `空列表永不判触底`() {
        assertFalse(shouldPrefetch(index = 0, totalCount = 0, prefetchDistance = 5))
        assertFalse(shouldPrefetch(index = 5, totalCount = 0, prefetchDistance = 5))
        assertFalse(shouldPrefetch(index = 0, totalCount = 0, prefetchDistance = 0))
    }

    @Test
    fun `提前量边界两侧判定相反`() {
        // 总 50 项、提前 5 项：界内起点为 50 - 5 = 45（与 core 的 shouldLoadMore 同一条判据）
        assertFalse(shouldPrefetch(index = 44, totalCount = 50, prefetchDistance = 5))
        assertTrue(shouldPrefetch(index = 45, totalCount = 50, prefetchDistance = 5))
        assertTrue(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = 5))

        // 提前 1 项：看见最后一项就取下一页（页面永远传 ≥1 的项数，见 prefetchItemCount）
        assertFalse(shouldPrefetch(index = 48, totalCount = 50, prefetchDistance = 1))
        assertTrue(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = 1))

        // 提前 0 项：core 判据的阈值等于总项数，永远不触发
        assertFalse(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = 0))
    }

    @Test
    fun `负下标与 core 判据同样不触发`() {
        // 页头还在屏幕里时「可见下标 - 行起点」是负数，不能因为提前量很大就判成触底
        assertFalse(shouldPrefetch(index = -1, totalCount = 50, prefetchDistance = 5))
        assertFalse(shouldPrefetch(index = -1, totalCount = 3, prefetchDistance = 10))
    }

    @Test
    fun `提前量大于列表长度时首屏即触底`() {
        assertTrue(shouldPrefetch(index = 0, totalCount = 3, prefetchDistance = 10))
        assertTrue(shouldPrefetch(index = 2, totalCount = 3, prefetchDistance = 10))
    }

    @Test
    fun `下标越界仍按触底处理`() {
        assertTrue(shouldPrefetch(index = 100, totalCount = 50, prefetchDistance = 5))
        assertTrue(shouldPrefetch(index = 50, totalCount = 51, prefetchDistance = 1))
    }

    @Test
    fun `负提前量不会提前触发`() {
        // 阈值被推到最后一项之外，等于「暂不预取」；页面侧的 prefetchItemCount 保证不会传负值
        assertFalse(shouldPrefetch(index = 48, totalCount = 50, prefetchDistance = -3))
        assertFalse(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = -3))
    }

    @Test
    fun `dp 换算按实测行高向上取整`() {
        assertEquals(5, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = 72f))
        assertEquals(5, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = 64f))
        assertEquals(4, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = 80f))
        assertEquals(4, prefetchItemCount(prefetchDistanceDp = 240f, rowHeightDp = 72f))
        assertEquals(1, prefetchItemCount(prefetchDistanceDp = 0f, rowHeightDp = 72f))
    }

    @Test
    fun `行高无效时退回名义行高`() {
        // 未布局出可见项时行高均值会是 NaN / 0，不能让换算结果变成 0 或抛异常
        assertEquals(5, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = Float.NaN))
        assertEquals(5, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = 0f))
        assertEquals(5, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = -12f))
        assertEquals(5, prefetchItemCount(prefetchDistanceDp = 320f, rowHeightDp = Float.POSITIVE_INFINITY))
    }

    @Test
    fun `页面侧判据按行起点换算并复用 feed 的开关`() {
        // 列表前 2 项是页头，feed 第 0 行对应可见下标 2
        val feed = PagedFeed(items = List(50) { it }, page = 1, hasMore = true)
        assertFalse(feed.shouldPrefetchRow(lastVisibleIndex = 46, rowStart = 2, prefetchDistance = 5))
        assertTrue(feed.shouldPrefetchRow(lastVisibleIndex = 47, rowStart = 2, prefetchDistance = 5))
        // 页头还在屏幕里：feed 行下标为负，不算触底
        assertFalse(feed.shouldPrefetchRow(lastVisibleIndex = 1, rowStart = 2, prefetchDistance = 10))

        // feed 级开关一票否决，调用点不必自己再判一遍
        assertFalse(feed.copy(hasMore = false).shouldPrefetchRow(lastVisibleIndex = 49, rowStart = 2, prefetchDistance = 5))
        assertFalse(feed.copy(loadingMore = true).shouldPrefetchRow(lastVisibleIndex = 49, rowStart = 2, prefetchDistance = 5))
        assertFalse(feed.copy(loadMoreFailed = true).shouldPrefetchRow(lastVisibleIndex = 49, rowStart = 2, prefetchDistance = 5))
        assertFalse(PagedFeed<Int>(hasMore = true).shouldPrefetchRow(lastVisibleIndex = 0, rowStart = 0, prefetchDistance = 5))
    }

    @Test
    fun `名次从第一页起算`() {
        assertEquals(1, rankOf(page = 1, stride = 20, index = 0))
        assertEquals(20, rankOf(page = 1, stride = 20, index = 19))
    }

    @Test
    fun `名次跨页续号`() {
        assertEquals(21, rankOf(page = 2, stride = 20, index = 0))
        assertEquals(40, rankOf(page = 2, stride = 20, index = 19))
        assertEquals(72, rankOf(page = 3, stride = 24, index = 23))
    }

    @Test
    fun `非法页码与步长按首项兜底`() {
        assertEquals(4, rankOf(page = 0, stride = 20, index = 3))
        assertEquals(4, rankOf(page = 1, stride = 0, index = 3))
        assertEquals(1, rankOf(page = 1, stride = 20, index = -5))
    }

    @Test
    fun `无限滚动追加一页后名次不整体后移`() {
        // 追加后 items 已含两页，下标 20 就是整份列表第 21 项，不能再叠加页偏移
        assertEquals(21, displayRank(clickPaging = false, page = 2, stride = 20, index = 20))
        assertEquals(1, displayRank(clickPaging = false, page = 1, stride = 20, index = 0))
        assertEquals(40, displayRank(clickPaging = false, page = 2, stride = 20, index = 39))
    }

    @Test
    fun `点页模式保留跨页续号`() {
        assertEquals(21, displayRank(clickPaging = true, page = 2, stride = 20, index = 0))
        assertEquals(72, displayRank(clickPaging = true, page = 3, stride = 24, index = 23))
    }

    @Test
    fun `分类模式列表头项只保留筛选与可选标签导航和结果标题`() {
        assertEquals(2, couiBrowseHeadCount(isCategoryMode = true, hasTagNav = false))
        assertEquals(3, couiBrowseHeadCount(isCategoryMode = true, hasTagNav = true))
        // 分类模式下周更在弹层内，不影响主列表起始下标
        assertEquals(2, couiBrowseHeadCount(isCategoryMode = true, hasTagNav = false, hasWeeklyPaging = true))
    }

    @Test
    fun `榜单模式列表头项包含周更区块与可选续页脚`() {
        assertEquals(3, couiBrowseHeadCount(isCategoryMode = false, hasTagNav = false, hasWeeklyPaging = false))
        assertEquals(4, couiBrowseHeadCount(isCategoryMode = false, hasTagNav = true, hasWeeklyPaging = false))
        assertEquals(5, couiBrowseHeadCount(isCategoryMode = false, hasTagNav = true, hasWeeklyPaging = true))
    }

    @Test
    fun `榜单模式周更展开为独立卡片后按标题加条数计入列表头项`() {
        assertEquals(
            9,
            couiBrowseHeadCount(
                isCategoryMode = false,
                hasTagNav = false,
                hasWeeklyPaging = true,
                weeklyItemCount = 5,
                hasWeeklyFooter = false,
            ),
        )
        assertEquals(
            11,
            couiBrowseHeadCount(
                isCategoryMode = false,
                hasTagNav = true,
                hasWeeklyPaging = true,
                weeklyItemCount = 5,
                hasWeeklyFooter = true,
            ),
        )
    }

    @Test
    fun `双列模式按行数与折算后的行级预取距离判定触底`() {
        // 20 条在双列下只有 10 行（下标 0..9），rowStart = 2 时末行可见下标为 11。
        // 行高折算出 prefetchDistance = 5（单列项数），双列按 ceil(5 / 2) = 3 行预取，阈值行下标 10 - 3 = 7（可见下标 9）。
        val feed = PagedFeed(items = List(20) { it }, page = 1, hasMore = true)
        assertFalse(feed.shouldPrefetchRow(lastVisibleIndex = 8, rowStart = 2, prefetchDistance = 5, columns = 2))
        assertTrue(feed.shouldPrefetchRow(lastVisibleIndex = 9, rowStart = 2, prefetchDistance = 5, columns = 2))
        assertTrue(feed.shouldPrefetchRow(lastVisibleIndex = 11, rowStart = 2, prefetchDistance = 5, columns = 2))

        // 奇数条向上取整成行数：21 条双列共 11 行（0..10），rowStart = 1（如追踪页带副标题），
        // distance = 2 -> ceil(2 / 2) = 1 行，仅看第 10 行（可见下标 11）触发，第 9 行（可见下标 10）不触发。
        val oddFeed = PagedFeed(items = List(21) { it }, page = 1, hasMore = true)
        assertFalse(oddFeed.shouldPrefetchRow(lastVisibleIndex = 10, rowStart = 1, prefetchDistance = 2, columns = 2))
        assertTrue(oddFeed.shouldPrefetchRow(lastVisibleIndex = 11, rowStart = 1, prefetchDistance = 2, columns = 2))
    }

    @Test
    fun `榜单模式双列周更按行数计入主列表头项`() {
        // 5 条周更在双列下占 3 行（ceil(5 / 2) = 3），不再按 5 项累加
        assertEquals(
            7,
            couiBrowseHeadCount(
                isCategoryMode = false,
                hasTagNav = false,
                hasWeeklyPaging = true,
                weeklyItemCount = 5,
                hasWeeklyFooter = false,
                columns = 2,
            ),
        )
    }

    @Test
    fun `总页数未知但仍有下一页时允许跳往下一页`() {
        val unknownTotalWithMore = PagedFeed(items = List(20) { it }, page = 1, pageCount = null, hasMore = true)
        assertTrue(couiCanJumpFeedPage(unknownTotalWithMore, target = 2))
        assertFalse(couiCanJumpFeedPage(unknownTotalWithMore, target = 1))
        assertFalse(couiCanJumpFeedPage(unknownTotalWithMore, target = 0))

        val unknownTotalEnd = PagedFeed(items = List(20) { it }, page = 2, pageCount = null, hasMore = false)
        assertTrue(couiCanJumpFeedPage(unknownTotalEnd, target = 1))
        assertFalse(couiCanJumpFeedPage(unknownTotalEnd, target = 3))

        val knownTotal = PagedFeed(items = List(20) { it }, page = 2, pageCount = 3, hasMore = true)
        assertTrue(couiCanJumpFeedPage(knownTotal, target = 3))
        assertFalse(couiCanJumpFeedPage(knownTotal, target = 4))
    }
}
