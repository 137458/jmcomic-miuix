package com.jm3.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 章节分组的契约：组身份由组内首章（原始顺序）决定，与展示方向无关。
 *
 * 目录切排序是展示行为，不该改变"哪些章节归为一组"——这正是小说目录
 * 切正序/倒序后展开态落到别的组的原因。
 */
class ChapterGroupsTest {

    private fun chapters(count: Int) = (1..count).map { "c$it" }

    private val keyOf: (String) -> String = { it }

    @Test
    fun 正序每十章一组且末组不补齐() {
        val groups = ChapterGroups.of(chapters(25), keyOf)

        assertEquals(listOf("c1", "c11", "c21"), groups.map { it.id })
        assertEquals(listOf(0, 10, 20), groups.map { it.startPosition })
        assertEquals(listOf(10, 10, 5), groups.map { it.items.size })
        assertEquals(chapters(10), groups[0].items)
        assertEquals(listOf("c21", "c22", "c23", "c24", "c25"), groups[2].items)
    }

    @Test
    fun 整十章节不产生空的尾组() {
        val groups = ChapterGroups.of(chapters(20), keyOf)

        assertEquals(listOf("c1", "c11"), groups.map { it.id })
        assertEquals(listOf(10, 10), groups.map { it.items.size })
    }

    @Test
    fun 倒序时组序列与组内条目一起反转() {
        val groups = ChapterGroups.of(chapters(25), keyOf, descending = true)

        assertEquals(listOf("c21", "c11", "c1"), groups.map { it.id })
        assertEquals(listOf("c25", "c24", "c23", "c22", "c21"), groups[0].items)
        assertEquals("c11", groups[1].id)
        assertEquals((20 downTo 11).map { "c$it" }, groups[1].items)
        assertEquals((10 downTo 1).map { "c$it" }, groups[2].items)
        assertEquals(listOf(20, 10, 0), groups.map { it.startPosition })
    }

    @Test
    fun 切换排序后同一组仍是同一批章节() {
        val ascending = ChapterGroups.of(chapters(25), keyOf)
        val descending = ChapterGroups.of(chapters(25), keyOf, descending = true)

        val ascendingByID = ascending.associate { it.id to it.items.toSet() }
        val descendingByID = descending.associate { it.id to it.items.toSet() }

        assertEquals(ascendingByID.keys, descendingByID.keys)
        assertEquals(ascendingByID, descendingByID)
    }

    @Test
    fun 不足十章的目录只有一组且倒序从末章开始() {
        val groups = ChapterGroups.of(chapters(3), keyOf, descending = true)

        assertEquals(1, groups.size)
        assertEquals("c1", groups[0].id)
        assertEquals(listOf("c3", "c2", "c1"), groups[0].items)
        assertEquals(0, groups[0].startPosition)
    }

    @Test
    fun 空目录不产生分组() {
        assertEquals(0, ChapterGroups.of(emptyList<String>(), keyOf).size)
        assertEquals(0, ChapterGroups.of(emptyList<String>(), keyOf, descending = true).size)
    }

    @Test
    fun 组内章节反查得出所属组的身份() {
        val items = chapters(25)

        assertEquals("c1", ChapterGroups.groupIdOf(items, keyOf, "c1"))
        assertEquals("c11", ChapterGroups.groupIdOf(items, keyOf, "c15"))
        assertEquals("c21", ChapterGroups.groupIdOf(items, keyOf, "c25"))
    }

    @Test
    fun 章节不在目录里时反查为空() {
        assertNull(ChapterGroups.groupIdOf(chapters(25), keyOf, "c99"))
        assertNull(ChapterGroups.groupIdOf(emptyList(), keyOf, "c1"))
    }
}
