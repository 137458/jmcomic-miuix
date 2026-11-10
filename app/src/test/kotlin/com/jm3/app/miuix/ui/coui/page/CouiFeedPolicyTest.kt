// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

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
        // 总 50 项、提前 5 项：最后一项下标 49，界内起点为 49 - 5 = 44
        assertFalse(shouldPrefetch(index = 43, totalCount = 50, prefetchDistance = 5))
        assertTrue(shouldPrefetch(index = 44, totalCount = 50, prefetchDistance = 5))
        assertTrue(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = 5))

        // 提前量为 0：只有真的看见最后一项才取下一页
        assertFalse(shouldPrefetch(index = 48, totalCount = 50, prefetchDistance = 0))
        assertTrue(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = 0))
    }

    @Test
    fun `提前量大于列表长度时首屏即触底`() {
        assertTrue(shouldPrefetch(index = 0, totalCount = 3, prefetchDistance = 10))
        assertTrue(shouldPrefetch(index = 2, totalCount = 3, prefetchDistance = 10))
    }

    @Test
    fun `下标越界仍按触底处理`() {
        assertTrue(shouldPrefetch(index = 100, totalCount = 50, prefetchDistance = 5))
        assertTrue(shouldPrefetch(index = 50, totalCount = 51, prefetchDistance = 0))
    }

    @Test
    fun `负提前量按零处理`() {
        assertFalse(shouldPrefetch(index = 48, totalCount = 50, prefetchDistance = -3))
        assertTrue(shouldPrefetch(index = 49, totalCount = 50, prefetchDistance = -3))
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
}
