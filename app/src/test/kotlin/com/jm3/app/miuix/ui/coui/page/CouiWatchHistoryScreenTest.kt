// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PageSlice
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 观看记录接口的三种结局折算：接口不返回总数，只能按「这一页是否装满」推断还有没有下一页，
 * 装满了就继续续页；会话失效与普通失败必须原样透传，屏幕据此弹登录或重试文案。
 */
class CouiWatchHistoryScreenTest {

    private fun results(count: Int): List<SearchResult> = List(count) { index ->
        SearchResult(
            id = "work-" + index,
            title = "作品" + index,
            authors = emptyList(),
            tags = emptyList(),
            coverUrl = null,
        )
    }

    private fun okSlice(outcome: SessionOutcome<PageSlice<SearchResult>>): PageSlice<SearchResult> =
        (outcome as SessionOutcome.Ok<PageSlice<SearchResult>>).value

    @Test
    fun 整页返回时判定还有下一页() {
        val slice = SessionOutcome.Ok(results(20)).toWatchSlice(page = 1, pageSize = 20)
        assertTrue(slice is SessionOutcome.Ok)
        val value = slice.value
        assertTrue(value.hasMore)
        assertEquals(1, value.page)
        assertEquals(20, value.items.size)
        assertNull(value.total)
        assertNull(value.pageCount)
    }

    @Test
    fun 不足一页时判定到头() {
        val slice = okSlice(SessionOutcome.Ok(results(19)).toWatchSlice(page = 1, pageSize = 20))
        assertFalse(slice.hasMore)
        assertEquals(19, slice.items.size)
    }

    @Test
    fun 空页判定到头() {
        val slice = okSlice(SessionOutcome.Ok(emptyList<SearchResult>()).toWatchSlice(page = 2, pageSize = 20))
        assertFalse(slice.hasMore)
        assertTrue(slice.items.isEmpty())
        assertEquals(2, slice.page)
    }

    @Test
    fun 非首页整页返回仍可续页() {
        val slice = okSlice(SessionOutcome.Ok(results(20)).toWatchSlice(page = 3, pageSize = 20))
        assertTrue(slice.hasMore)
        assertEquals(3, slice.page)
    }

    @Test
    fun 会话失效原样透传() {
        val slice = SessionOutcome.Expired.toWatchSlice(page = 1, pageSize = 20)
        assertSame(SessionOutcome.Expired, slice)
    }

    @Test
    fun 普通失败原样透传原文与异常() {
        val cause = IllegalStateException("网关返回 500")
        val failed = SessionOutcome.Failed("加载失败", cause)
        val slice = failed.toWatchSlice(page = 2, pageSize = 20)
        assertSame(failed, slice)
        val failedSlice = slice as SessionOutcome.Failed
        assertEquals("加载失败", failedSlice.message)
        assertSame(cause, failedSlice.cause)
    }
}
