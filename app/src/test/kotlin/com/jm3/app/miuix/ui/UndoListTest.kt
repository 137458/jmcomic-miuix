package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class UndoListTest {

    private data class Item(val id: String, val group: String)

    @Test
    fun 摘除并撤销会回到原始位置而非跳到顶部() {
        val initial = listOf(
            Item("a", "g1"),
            Item("b1", "g2"),
            Item("b2", "g2"),
            Item("c", "g3"),
        )
        val slice = initial.removeMatching { it.group == "g2" }
        assertEquals(listOf(Item("b1", "g2"), Item("b2", "g2")), slice.removed)
        assertEquals(listOf(Item("a", "g1"), Item("c", "g3")), slice.remaining)
        assertEquals(1, slice.insertIndex)

        val restored = slice.remaining.restoreAt(slice.removed, slice.insertIndex) { it.id }
        assertEquals(initial, restored)
    }

    @Test
    fun 列表在撤销前缩短时下标钳制到末尾并去重() {
        val initial = listOf(
            Item("a", "g1"),
            Item("b", "g2"),
            Item("c", "g3"),
            Item("d", "g4"),
        )
        val slice = initial.removeMatching { it.group == "g4" }
        assertEquals(3, slice.insertIndex)

        // 并发删除了 c 和 a，并且期间外部重新写入过 d
        val currentAfterOtherDeletes = listOf(Item("b", "g2"), Item("d", "g4"))
        val restored = currentAfterOtherDeletes.restoreAt(slice.removed, slice.insertIndex) { it.id }
        assertEquals(listOf(Item("b", "g2"), Item("d", "g4")), restored)

        // 不在当前列表中的项钳制插入到末尾
        val shorter = listOf(Item("b", "g2"))
        val clamped = shorter.restoreAt(slice.removed, slice.insertIndex) { it.id }
        assertEquals(listOf(Item("b", "g2"), Item("d", "g4")), clamped)
    }

    @Test
    fun 未命中摘除条件或恢复空列表时保持原样() {
        val initial = listOf(Item("a", "g1"), Item("b", "g2"))
        val miss = initial.removeMatching { it.group == "none" }
        assertEquals(emptyList(), miss.removed)
        assertEquals(initial, miss.remaining)
        assertEquals(initial, initial.restoreAt(miss.removed, miss.insertIndex) { it.id })
        assertEquals(initial, initial.restoreAt(emptyList(), insertIndex = -5) { it.id })
    }
}
