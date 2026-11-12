package com.jm3.core

import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.paging.PagedFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentPagesTest {

    private fun item(id: String) = CommentItem(id, "作者$id", "内容", null, 0, emptyList())

    @Test
    fun 合并_拼接去重并取新分页信息() {
        val base = CommentPage(total = 5, items = listOf(item("c1"), item("c2")))
        val next = CommentPage(total = 5, items = listOf(item("c2"), item("c3")))
        val got = base.mergedWith(next)
        assertEquals(listOf("c1", "c2", "c3"), got.items.map { it.id })
        assertEquals(5, got.total)
    }

    @Test
    fun 合并_空追加_保持原样() {
        val base = CommentPage(total = 2, items = listOf(item("c1")))
        val got = base.mergedWith(CommentPage(total = 2, items = emptyList()))
        assertEquals(listOf("c1"), got.items.map { it.id })
        assertEquals(2, got.total)
    }

    @Test
    fun appendedBy_空页或全重复页即使total更大也不再继续加载() {
        val base = PagedFeed<CommentItem>().replacedBy(
            CommentPage(total = 10, items = listOf(item("c1"), item("c2"))),
        )
        assertTrue(base.hasMore)

        val afterEmpty = base.appendedBy(CommentPage(total = 10, items = emptyList()))
        assertFalse(afterEmpty.hasMore)

        val afterAllDup = base.appendedBy(CommentPage(total = 10, items = listOf(item("c1"), item("c2"))))
        assertFalse(afterAllDup.hasMore)

        val afterPartialNew = base.appendedBy(CommentPage(total = 10, items = listOf(item("c2"), item("c3"))))
        assertTrue(afterPartialNew.hasMore)
        assertEquals(listOf("c1", "c2", "c3"), afterPartialNew.items.map { it.id })
    }
}
