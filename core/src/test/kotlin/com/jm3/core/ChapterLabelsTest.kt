package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterLabelsTest {

    @Test
    fun `空白标题回落为话数序号`() {
        assertEquals("第1话", ChapterLabels.chapterTitle("", 0)) // 边界：首话
        assertEquals("第2话", ChapterLabels.chapterTitle("   ", 1)) // 纯空白同样回落
        assertEquals("第12话", ChapterLabels.chapterTitle("", 11))
    }

    @Test
    fun `有标题时原样保留`() {
        assertEquals("第03.1話", ChapterLabels.chapterTitle("第03.1話", 0))
        assertEquals("番外", ChapterLabels.chapterTitle("番外", 5))
    }

    @Test
    fun `组标题拼接首末非空标题`() {
        assertEquals("第01話 ~ 第9.2話", ChapterLabels.groupTitle(listOf("第01話", "第9.2話"), 0))
        assertEquals(
            "第03.1話 ~ 第04.2話",
            ChapterLabels.groupTitle(listOf("", "第03.1話", "第04.2話", ""), 0),
        )
    }

    @Test
    fun `只有一个已知标题时不出现悬空分隔符`() {
        assertEquals("第03.1話", ChapterLabels.groupTitle(listOf("", "第03.1話"), 0))
        assertEquals("第03.1話", ChapterLabels.groupTitle(listOf("第03.1話", "", ""), 0))
    }

    @Test
    fun `全部标题缺失时回落为话数区间`() {
        assertEquals("第1–10话", ChapterLabels.groupTitle(List(10) { "" }, 0)) // 边界：第一组
        assertEquals("第11–20话", ChapterLabels.groupTitle(List(10) { "" }, 10)) // 第二组延续编号
        assertEquals("第7话", ChapterLabels.groupTitle(listOf(""), 6)) // 单话组
    }
}
