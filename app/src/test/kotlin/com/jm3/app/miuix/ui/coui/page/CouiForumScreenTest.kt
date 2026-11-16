// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 论坛评论页折算成 [com.jm3.core.paging.PageSlice] 的末页判据。
 *
 * 评论接口不返回页码，只给 total：total 大于 0 时按总页数判末页，total 为 0 或负数表示总数未知，
 * 此时只能按「本页有没有条目」推断，否则空页会被当成还有下一页，无限滚动会一直打空请求。
 */
class CouiForumScreenTest {

    private fun item(id: String): CommentItem = CommentItem(
        id = id,
        author = "作者",
        content = "正文",
        postDate = null,
        likes = 0,
        replies = emptyList(),
    )

    @Test
    fun 总数未知时按本页条目推断还有下一页() {
        val slice = couiForumSlice(CommentPage(total = 0, items = listOf(item("1"))), requestPage = 1)
        assertTrue(slice.hasMore)
        assertNull(slice.total)
        assertNull(slice.pageCount)
        assertEquals(1, slice.page)
        assertEquals(1, slice.items.size)
    }

    @Test
    fun 总数未知且本页为空时判定到头() {
        val slice = couiForumSlice(CommentPage(total = 0, items = emptyList()), requestPage = 3)
        assertFalse(slice.hasMore)
        assertNull(slice.total)
        assertNull(slice.pageCount)
        assertEquals(3, slice.page)
        assertTrue(slice.items.isEmpty())
    }

    @Test
    fun 总数已知时前页仍可续() {
        // 25 条按每页 10 条折成 3 页，请求第 1 页后面还有两页
        val slice = couiForumSlice(CommentPage(total = 25, items = listOf(item("1"))), requestPage = 1)
        assertTrue(slice.hasMore)
        assertEquals(25, slice.total)
        assertEquals(3, slice.pageCount)
        assertEquals(1, slice.page)
    }

    @Test
    fun 总数已知时末页不再续() {
        val slice = couiForumSlice(CommentPage(total = 25, items = listOf(item("21"))), requestPage = 3)
        assertFalse(slice.hasMore)
        assertEquals(3, slice.pageCount)
        assertEquals(3, slice.page)
    }

    @Test
    fun 请求页超出总页数时不再续() {
        val slice = couiForumSlice(CommentPage(total = 10, items = emptyList()), requestPage = 2)
        assertFalse(slice.hasMore)
        assertEquals(10, slice.total)
        assertEquals(1, slice.pageCount)
        assertEquals(2, slice.page)
    }

    @Test
    fun 负数总数按总数未知处理() {
        val slice = couiForumSlice(CommentPage(total = -3, items = listOf(item("1"))), requestPage = 1)
        assertTrue(slice.hasMore)
        assertNull(slice.total)
        assertNull(slice.pageCount)
    }
}
