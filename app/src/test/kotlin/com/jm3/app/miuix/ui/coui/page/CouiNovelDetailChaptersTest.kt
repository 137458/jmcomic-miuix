// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.reader.ChapterGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 小说目录行规划与章节标记契约：
 * - 单章分组不提供折叠开关且默认展开；
 * - 多章分组仅展开命中的 [expandedGroupId]；
 * - 单章摘要优先级严格遵循 上次读到 > 需购买 > 新章节 > 已购买 > null。
 */
class CouiNovelDetailChaptersTest {

    private fun chapter(
        ncid: String,
        sort: String? = ncid,
        isNew: Boolean = false,
        purchased: Boolean = false,
        needBuy: Boolean = false,
    ) = NovelChapterMeta(
        ncid = ncid,
        title = "第${ncid}章",
        sort = sort,
        isNew = isNew,
        purchased = purchased,
        needBuy = needBuy,
    )

    @Test
    fun 章节摘要严格按既定优先级判定() {
        val allFlags = chapter("1", isNew = true, purchased = true, needBuy = true)
        assertEquals("上次读到", novelChapterMarker(allFlags, savedChapterId = "1"))
        assertEquals("需购买", novelChapterMarker(allFlags, savedChapterId = "other"))

        val newAndPurchased = chapter("2", isNew = true, purchased = true, needBuy = false)
        assertEquals("新章节", novelChapterMarker(newAndPurchased, savedChapterId = null))

        val purchasedOnly = chapter("3", purchased = true)
        assertEquals("已购买", novelChapterMarker(purchasedOnly, savedChapterId = null))

        val plain = chapter("4")
        assertNull(novelChapterMarker(plain, savedChapterId = null))
    }

    @Test
    fun 单章分组强制展开且隐藏折叠开关() {
        val singleGroup = ChapterGroup(
            id = "g-single",
            items = listOf(chapter("10")),
            startPosition = 0,
        )

        val rows = planNovelChapterRows(
            groups = listOf(singleGroup),
            expandedGroupId = null,
            savedChapterId = null,
        )

        assertEquals(2, rows.size)
        val header = assertIs<CouiNovelChapterRow.GroupHeader>(rows[0])
        assertFalse(header.showToggle)
        assertTrue(header.expanded)
        val line = assertIs<CouiNovelChapterRow.ChapterLine>(rows[1])
        assertEquals("10", line.chapter.ncid)
        assertFalse(line.highlighted)
    }

    @Test
    fun 多章分组仅展开命中组并高亮上次读到章节() {
        val group1 = ChapterGroup(
            id = "g1",
            items = listOf(chapter("1"), chapter("2", needBuy = true)),
            startPosition = 0,
        )
        val group2 = ChapterGroup(
            id = "g2",
            items = listOf(chapter("3", isNew = true), chapter("4", purchased = true)),
            startPosition = 2,
        )

        val rows = planNovelChapterRows(
            groups = listOf(group1, group2),
            expandedGroupId = "g2",
            savedChapterId = "3",
        )

        assertEquals(4, rows.size)
        val h1 = assertIs<CouiNovelChapterRow.GroupHeader>(rows[0])
        assertTrue(h1.showToggle)
        assertFalse(h1.expanded)
        assertEquals("2 章 · 点击展开", h1.summary)

        val h2 = assertIs<CouiNovelChapterRow.GroupHeader>(rows[1])
        assertTrue(h2.showToggle)
        assertTrue(h2.expanded)
        assertEquals("2 章 · 点击收起", h2.summary)

        val c3 = assertIs<CouiNovelChapterRow.ChapterLine>(rows[2])
        assertEquals("上次读到", c3.marker)
        assertTrue(c3.highlighted)

        val c4 = assertIs<CouiNovelChapterRow.ChapterLine>(rows[3])
        assertEquals("已购买", c4.marker)
        assertFalse(c4.highlighted)
    }
}
