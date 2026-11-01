// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.model.NovelChapterMeta
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
    fun 正序分组每十章一组且尾组不补齐() {
        val chapters = (1..25).map { chapter(it.toString()) }

        val groups = novelChapterGroups(chapters, ascending = true)

        assertEquals(3, groups.size)
        assertEquals(listOf("1", "10"), listOf(groups[0].first().sort, groups[0].last().sort))
        assertEquals(listOf("21", "25"), listOf(groups[2].first().sort, groups[2].last().sort))
    }

    @Test
    fun 倒序分组从末章开始往前切() {
        val chapters = (1..12).map { chapter(it.toString()) }

        val groups = novelChapterGroups(chapters, ascending = false)

        assertEquals(2, groups.size)
        assertEquals(listOf("12", "3"), listOf(groups[0].first().sort, groups[0].last().sort))
        assertEquals(listOf("2", "1"), listOf(groups[1].first().sort, groups[1].last().sort))
    }

    @Test
    fun 空目录不产生分组() {
        assertEquals(0, novelChapterGroups(emptyList(), ascending = true).size)
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
