// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.model.CommentItem
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.paging.PagedFeed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovelReaderLogicTest {

    private fun chapter(id: String) = NovelChapterMeta(
        ncid = id,
        title = "第 $id 章",
        sort = id,
        isNew = false,
        purchased = false,
        needBuy = false,
    )

    @Test
    fun `当前章节不在目录中时上一章与下一章都返回空`() {
        val chapters = listOf(chapter("1"), chapter("2"), chapter("3"))

        val (prev, next) = novelAdjacentChapters(chapters, currentId = "missing")

        assertNull(prev)
        assertNull(next)
    }

    @Test
    fun `当前章节在目录中间时正确返回上一章与下一章`() {
        val chapters = listOf(chapter("1"), chapter("2"), chapter("3"))

        val (prev, next) = novelAdjacentChapters(chapters, currentId = "2")

        assertEquals("1", prev?.ncid)
        assertEquals("3", next?.ncid)
    }

    @Test
    fun `首章没有上一章且末章没有下一章`() {
        val chapters = listOf(chapter("1"), chapter("2"))

        val (firstPrev, firstNext) = novelAdjacentChapters(chapters, currentId = "1")
        assertNull(firstPrev)
        assertEquals("2", firstNext?.ncid)

        val (lastPrev, lastNext) = novelAdjacentChapters(chapters, currentId = "2")
        assertEquals("1", lastPrev?.ncid)
        assertNull(lastNext)
    }

    @Test
    fun `收藏动作在途期间连点被拦截且完成后自动复位`() = runBlocking {
        val gate = NovelReaderActionGate()
        val firstStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        var invocations = 0

        val firstJob = async {
            gate.run {
                invocations++
                firstStarted.complete(Unit)
                allowFinish.await()
            }
        }
        firstStarted.await()
        assertTrue(gate.busy)

        val secondAccepted = gate.run {
            invocations++
        }

        assertFalse(secondAccepted)
        assertEquals(1, invocations)

        allowFinish.complete(Unit)
        assertTrue(firstJob.await())
        assertFalse(gate.busy)

        val thirdAccepted = gate.run {
            invocations++
        }
        assertTrue(thirdAccepted)
        assertEquals(2, invocations)
    }

    @Test
    fun `评论面板已有列表时重刷第一页失败应保留旧列表而不是抹空`() {
        val existing = PagedFeed(
            items = listOf(
                CommentItem(
                    id = "c1",
                    author = "作者",
                    content = "评论内容",
                    postDate = "2026-11-12",
                    likes = 0,
                    replies = emptyList(),
                ),
            ),
            page = 1,
            hasMore = false,
        )

        assertTrue(novelCommentsKeepContentOnFailure(existing, replace = true))
        assertTrue(novelCommentsKeepContentOnFailure(existing, replace = false))
        assertFalse(novelCommentsKeepContentOnFailure(PagedFeed<CommentItem>(), replace = true))
    }
}
