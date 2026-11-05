// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.reader.ChapterGroups
import kotlin.test.Test
import kotlin.test.assertEquals

class NovelChapterLogicTest {

    private fun chapter(sort: String?, title: String = "") = NovelChapterMeta(
        ncid = "nc-${sort ?: title}",
        title = title,
        sort = sort,
        isNew = false,
        purchased = false,
        needBuy = false,
    )

    @Test
    fun 切排序后同一批章节仍归同一组() {
        val chapters = (1..12).map { chapter(it.toString()) }

        val ascending = ChapterGroups.of(chapters, NovelChapterMeta::ncid)
        val descending = ChapterGroups.of(chapters, NovelChapterMeta::ncid, descending = true)

        // 组序列随展示方向整体反转，但每组承载的仍是同一批章节
        assertEquals(ascending.map { it.id }.reversed(), descending.map { it.id })
        assertEquals(
            ascending.associate { it.id to it.items.map { item -> item.ncid }.toSet() },
            descending.associate { it.id to it.items.map { item -> item.ncid }.toSet() },
        )
        // 旧实现先整体反转再按十切，倒序会把 nc-3..nc-12 切成一组，与正序分区不一致
        assertEquals(listOf("nc-11", "nc-12"), ascending.last().items.map { it.ncid })
        assertEquals(listOf("nc-12", "nc-11"), descending.first().items.map { it.ncid })
    }

    @Test
    fun 组标题取首末序号() {
        val group = (3..12).map { chapter(it.toString()) }

        assertEquals("#3 ~ #12", novelGroupTitle(group))
    }

    @Test
    fun 单章组标题只给一个序号() {
        assertEquals("#7", novelGroupTitle(listOf(chapter("7"))))
    }

    @Test
    fun 序号缺失时退回首末章节名() {
        val group = listOf(chapter(null, "序章"), chapter(null, "尾声"))

        assertEquals("序章 ~ 尾声", novelGroupTitle(group))
    }

    @Test
    fun 序号与章节名都缺失时按条数回落() {
        val group = listOf(chapter(null, ""), chapter(null, " "))

        assertEquals("2 章", novelGroupTitle(group))
    }
}
