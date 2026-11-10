// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.CommentItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 评论扁平化契约：懒列表只能按行渲染，嵌套回复必须在进入列表前摊平成带层级的行。
 *
 * 折叠规则与旧的自绘递归保持一致：默认露前两条回复，多出来的收在「展开 N 条回复」行后面，
 * 展开后同一位置换成「收起回复」。层级只影响缩进，不改变「父在前、子随后」的顺序。
 */
class FlattenCommentsTest {

    private fun comment(id: String, replies: List<CommentItem> = emptyList()) = CommentItem(
        id = id,
        author = "作者$id",
        content = "内容$id",
        postDate = null,
        likes = 0,
        replies = replies,
    )

    @Test
    fun 空列表摊平后仍为空() {
        assertEquals(emptyList<CommentRow>(), flattenComments(emptyList()))
    }

    @Test
    fun 单层评论按原顺序摊平且层级为零() {
        val rows = flattenComments(listOf(comment("a"), comment("b"), comment("c")))

        assertEquals(listOf("a", "b", "c"), rows.map { it.key })
        assertTrue(rows.all { it is CommentRow.Comment && it.depth == 0 })
    }

    @Test
    fun 嵌套回复紧跟父行且逐层记录深度() {
        val nested = listOf(
            comment(
                "a",
                replies = listOf(
                    comment("a1", replies = listOf(comment("a1x"))),
                    comment("a2"),
                ),
            ),
            comment("b"),
        )

        val rows = flattenComments(nested)

        assertEquals(listOf("a", "a1", "a1x", "a2", "b"), rows.map { it.key })
        assertEquals(listOf(0, 1, 2, 1, 0), rows.map { (it as CommentRow.Comment).depth })
    }

    @Test
    fun 回复超过可见条数时折叠出展开行() {
        val rows = flattenComments(
            listOf(comment("a", replies = listOf(comment("a1"), comment("a2"), comment("a3")))),
        )

        assertEquals(listOf("a", "a1", "a2", "reply-toggle-a"), rows.map { it.key })
        val toggle = rows.last() as CommentRow.RepliesToggle
        assertEquals("a", toggle.parentId)
        assertEquals(1, toggle.hiddenCount)
        assertFalse(toggle.expanded)
        assertEquals(0, toggle.depth)
    }

    @Test
    fun 展开后回复全出并换成收起行() {
        val rows = flattenComments(
            roots = listOf(comment("a", replies = listOf(comment("a1"), comment("a2"), comment("a3")))),
            expandedIds = setOf("a"),
        )

        assertEquals(listOf("a", "a1", "a2", "a3", "reply-toggle-a"), rows.map { it.key })
        val toggle = rows.last() as CommentRow.RepliesToggle
        assertTrue(toggle.expanded)
        assertEquals(1, toggle.hiddenCount)
    }

    @Test
    fun 折叠只收当前分支不波及其他评论() {
        val rows = flattenComments(
            roots = listOf(
                comment("a", replies = listOf(comment("a1"), comment("a2"), comment("a3"))),
                comment("b", replies = listOf(comment("b1"), comment("b2"), comment("b3"))),
            ),
            expandedIds = setOf("a"),
        )

        assertEquals(
            listOf("a", "a1", "a2", "a3", "reply-toggle-a", "b", "b1", "b2", "reply-toggle-b"),
            rows.map { it.key },
        )
        val reopened = rows.last() as CommentRow.RepliesToggle
        assertEquals("b", reopened.parentId)
        assertFalse(reopened.expanded)
    }

    @Test
    fun 行键在整棵树内唯一() {
        val rows = flattenComments(
            roots = listOf(
                comment("a", replies = listOf(comment("a1"), comment("a2"), comment("a3"))),
                comment("b"),
            ),
            expandedIds = setOf("a"),
        )

        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `重复 id 的评论只保留首次整棵子树`() {
        val rows = flattenComments(
            roots = listOf(
                comment("a", replies = listOf(comment("a1"), comment("a2"), comment("a3"))),
                comment("a", replies = listOf(comment("a9"))),
                comment("b"),
            ),
        )

        assertEquals(listOf("a", "a1", "a2", "reply-toggle-a", "b"), rows.map { it.key })
        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
