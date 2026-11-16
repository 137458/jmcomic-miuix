package com.jm3.app.miuix

import com.jm3.core.settings.BottomBarSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BottomBarSizePolicyTest {

    @Test
    fun `偏大档原样落盘`() {
        assertEquals(BottomBarSize.GENEROUS, explicitBottomBarSize(BottomBarSize.GENEROUS))
    }

    @Test
    fun `普通档清空显式值而不是写死标准档`() {
        // 写死 STANDARD 会让平板与宽屏窗口的断点再也解析不出更大的默认值。
        assertNull(explicitBottomBarSize(BottomBarSize.STANDARD))
    }

    @Test
    fun `所有档位只有偏大档需要落盘`() {
        val persisted = BottomBarSize.entries.filter { explicitBottomBarSize(it) != null }

        assertEquals(listOf(BottomBarSize.GENEROUS), persisted)
    }
}
