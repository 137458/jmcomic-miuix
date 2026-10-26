package com.jm3.core

import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
