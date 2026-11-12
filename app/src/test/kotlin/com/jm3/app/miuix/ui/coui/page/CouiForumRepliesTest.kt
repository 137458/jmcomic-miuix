// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 论坛楼层回复扁平化契约：按深度优先保留「父在前、子随后」顺序，
 * 且缩进层级最多保留两层（depth = 1..2），防止深层嵌套挤窄正文。
 */
class CouiForumRepliesTest {

    private fun comment(id: String, replies: List<CommentItem> = emptyList()) = CommentItem(
        id = id,
        author = "用户$id",
        content = "正文$id",
        postDate = null,
        likes = 0,
        replies = replies,
    )

    @Test
    fun 无回复时扁平化为空() {
        assertEquals(emptyList(), flattenForumReplies(comment("root")))
    }

    @Test
    fun 多层回复按深度优先摊平且层级封顶为二() {
        val root = comment(
            id = "root",
            replies = listOf(
                comment(
                    id = "r1",
                    replies = listOf(
                        comment(
                            id = "r1_1",
                            replies = listOf(
                                comment(
                                    id = "r1_1_1",
                                    replies = listOf(comment("r1_1_1_1")),
                                ),
                            ),
                        ),
                    ),
                ),
                comment("r2"),
            ),
        )

        val rows = flattenForumReplies(root)

        assertEquals(listOf("r1", "r1_1", "r1_1_1", "r1_1_1_1", "r2"), rows.map { it.first.id })
        assertEquals(listOf(1, 2, 2, 2, 1), rows.map { it.second })
    }

    @Test
    fun 回复刷新合并多页评论时保留前序页条目与游标() {
        val page1 = CommentPage(total = 25, items = listOf(comment("1"), comment("2")))
        val page2 = CommentPage(total = 25, items = listOf(comment("2"), comment("3")))
        val page3 = CommentPage(total = 25, items = listOf(comment("4", replies = listOf(comment("r1")))))

        val merged = mergeCouiForumPages(listOf(page1, page2, page3), activePage = 3, pageSize = 10)

        assertEquals(listOf("1", "2", "3", "4"), merged.items.map { it.id })
        assertEquals(listOf("r1"), merged.items.last().replies.map { it.id })
        assertEquals(3, merged.page)
        assertEquals(3, merged.pageCount)
    }
}
