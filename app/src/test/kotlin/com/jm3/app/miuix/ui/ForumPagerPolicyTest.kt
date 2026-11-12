// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.model.CommentItem
import com.jm3.core.paging.PagedFeed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForumPagerPolicyTest {

    private val sampleItem = CommentItem(
        id = "t1",
        author = "用户",
        content = "帖子内容",
        postDate = "2026-11-12",
        likes = 0,
        replies = emptyList(),
    )

    @Test
    fun `总页数未知但仍有下一页时允许显示翻页条`() {
        val feed = PagedFeed(
            items = listOf(sampleItem),
            page = 1,
            pageCount = null,
            hasMore = true,
        )

        assertTrue(shouldShowForumPager(feed))
    }

    @Test
    fun `总页数未知且位于后续页即使无更多页仍需显示翻页条以便返回上一页`() {
        val feed = PagedFeed(
            items = listOf(sampleItem),
            page = 2,
            pageCount = null,
            hasMore = false,
        )

        assertTrue(shouldShowForumPager(feed))
    }

    @Test
    fun `单页且无更多页时不显示翻页条`() {
        val feed = PagedFeed(
            items = listOf(sampleItem),
            page = 1,
            pageCount = 1,
            hasMore = false,
        )

        assertFalse(shouldShowForumPager(feed))
    }
}
