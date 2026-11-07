package com.jm3.app.miuix.ui

import com.jm3.core.settings.PaginationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 设置页分页下拉的选中判定。
 *
 * 修复的缺陷：设置页曾把模式抄进本地 remember，列表页读全局真源，于是「切换无效」。
 * 这里钉住选中项到全局模式的映射，以及「同值不重复落盘」这一条。
 */
class SettingsPaginationSelectionTest {

    @Test
    fun `selecting the current mode reports no change`() {
        assertEquals(
            PaginationModeSelection(PaginationMode.INFINITE, changed = false),
            resolvePaginationSelection(PaginationMode.INFINITE, 0),
        )
        assertEquals(
            PaginationModeSelection(PaginationMode.CLICK, changed = false),
            resolvePaginationSelection(PaginationMode.CLICK, 1),
        )
    }

    @Test
    fun `selecting the other mode reports a change`() {
        assertEquals(
            PaginationModeSelection(PaginationMode.CLICK, changed = true),
            resolvePaginationSelection(PaginationMode.INFINITE, 1),
        )
        assertEquals(
            PaginationModeSelection(PaginationMode.INFINITE, changed = true),
            resolvePaginationSelection(PaginationMode.CLICK, 0),
        )
    }

    @Test
    fun `out of range indices are ignored`() {
        assertNull(resolvePaginationSelection(PaginationMode.INFINITE, -1))
        assertNull(resolvePaginationSelection(PaginationMode.INFINITE, 2))
        assertNull(resolvePaginationSelection(PaginationMode.CLICK, 99))
    }

    @Test
    fun `every enum value is reachable from its index`() {
        PaginationMode.entries.forEachIndexed { index, mode ->
            assertEquals(
                PaginationModeSelection(mode, changed = mode != PaginationMode.INFINITE),
                resolvePaginationSelection(PaginationMode.INFINITE, index),
            )
        }
    }
}
