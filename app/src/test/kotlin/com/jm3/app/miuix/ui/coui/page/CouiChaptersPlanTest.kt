// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiChaptersPlanTest {

    private fun chapters(count: Int, titleOf: (Int) -> String = { "第${it + 1}话" }): List<Chapter> =
        (0 until count).map { index -> Chapter(id = "c$index", title = titleOf(index), sortOrder = index) }

    private fun headers(plan: CouiChaptersPlan): List<CouiChapterRow.GroupHeader> =
        plan.rows.filterIsInstance<CouiChapterRow.GroupHeader>()

    private fun lines(plan: CouiChaptersPlan): List<CouiChapterRow.ChapterLine> =
        plan.rows.filterIsInstance<CouiChapterRow.ChapterLine>()

    @Test
    fun `空章节只产出一行空态`() {
        val plan = planCouiChapters(emptyList(), emptySet(), null, emptySet())

        assertEquals(0, plan.totalChapters)
        assertEquals(listOf(CouiChapterRow.Empty), plan.rows)
        assertFalse(plan.showBatchAction)
        assertFalse(plan.allExpanded)
    }

    @Test
    fun `单话组没有展开开关但直接列出章节行`() {
        // 11 话切成 10 + 1 两组：末组只有一话，没有折叠态，直接列出该话
        val plan = planCouiChapters(chapters(11), setOf("c0"), null, emptySet())

        val header = headers(plan).last()
        assertEquals("第11话", header.title)
        assertFalse(header.showToggle)
        assertEquals("", header.summary)
        assertEquals(11, lines(plan).size)
        assertEquals("第11话", lines(plan).last().title)
        assertFalse(plan.showBatchAction)
    }

    @Test
    fun `折叠的多话组只留组头`() {
        val plan = planCouiChapters(chapters(12), emptySet(), null, emptySet())

        assertEquals(2, headers(plan).size)
        assertEquals(0, lines(plan).size)
        val first = headers(plan).first()
        assertTrue(first.showToggle)
        assertFalse(first.expanded)
        assertEquals("10 话 · 点击展开", first.summary)
        assertTrue(plan.showBatchAction)
        assertFalse(plan.allExpanded)
        assertEquals(listOf("c0", "c10"), plan.collapsibleGroupIds)
    }

    @Test
    fun `展开的组追加十行且组序号跨组连续`() {
        val plan = planCouiChapters(chapters(12), setOf("c0"), null, emptySet())

        assertEquals(listOf("第1话", "第2话", "第3话", "第4话", "第5话", "第6话", "第7话", "第8话", "第9话", "第10话"), lines(plan).map { it.title })
        assertEquals(12, plan.rows.size)
        assertTrue(headers(plan).first().expanded)
        assertEquals("10 话 · 点击收起", headers(plan).first().summary)
        assertEquals("第11话 ~ 第12话", headers(plan).last().title)
        assertEquals("c10", headers(plan).last().groupId)
    }

    @Test
    fun `全部展开判定只看可折叠组`() {
        val plan = planCouiChapters(chapters(12), setOf("c0", "c10"), null, emptySet())

        assertTrue(plan.allExpanded)
        assertTrue(headers(plan).all { it.expanded })
        assertEquals(14, plan.rows.size)
    }

    @Test
    fun `上次读到优先于已读且高亮`() {
        // 三话同属一组，先展开才拿得到章节行
        val plan = planCouiChapters(chapters(3), setOf("c0"), "c1", setOf("c1", "c2"))

        val marked = lines(plan).associate { it.chapter.id to (it.marker to it.highlighted) }
        assertEquals("上次读到" to true, marked["c1"])
        assertEquals("已读" to false, marked["c2"])
        assertEquals(null to false, marked["c0"])
    }

    @Test
    fun `空标题按跨组序号回落到第N话`() {
        val plan = planCouiChapters(chapters(12) { "" }, setOf("c0", "c10"), null, emptySet())

        assertEquals("第1–10话", headers(plan).first().title)
        assertEquals((1..12).map { "第${it}话" }, lines(plan).map { it.title })
    }

    @Test
    fun `全部展开时批量只点名已展开的组`() {
        val plan = planCouiChapters(chapters(12), setOf("c0", "c10"), null, emptySet())

        assertTrue(plan.allExpanded)
        assertEquals(listOf("c0", "c10"), couiChapterBatchToggleIds(plan, setOf("c0", "c10")))
    }

    @Test
    fun `未全展开时批量只点名还没展开的组`() {
        val plan = planCouiChapters(chapters(12), setOf("c0"), null, emptySet())

        assertFalse(plan.allExpanded)
        assertEquals(listOf("c10"), couiChapterBatchToggleIds(plan, setOf("c0")))
        assertEquals(listOf("c0", "c10"), couiChapterBatchToggleIds(plan, emptySet()))
    }

    @Test
    fun `行键唯一且内容类型区分组头与章节`() {
        val plan = planCouiChapters(chapters(12), setOf("c0", "c10"), "c3", setOf("c4"))

        val keys = plan.rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("chapter-group", "chapter"), plan.rows.map { it.contentType }.distinct())
    }
}
