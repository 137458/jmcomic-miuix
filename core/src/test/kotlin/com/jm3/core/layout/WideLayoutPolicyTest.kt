package com.jm3.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WideLayoutPolicyTest {

    @Test
    fun `断点划分复用 600dp 并新增 840dp`() {
        assertEquals(WindowWidthClass.COMPACT, WideLayoutPolicy.widthClass(599))
        assertEquals(WindowWidthClass.MEDIUM, WideLayoutPolicy.widthClass(600))
        assertEquals(WindowWidthClass.MEDIUM, WideLayoutPolicy.widthClass(839))
        assertEquals(WindowWidthClass.EXPANDED, WideLayoutPolicy.widthClass(840))
    }

    @Test
    fun `手机断点以下列数保持两列`() {
        // 断点以下完全沿用既有布局：360dp 与 599dp 都是两列
        assertEquals(2, WideLayoutPolicy.gridColumns(360))
        assertEquals(2, WideLayoutPolicy.gridColumns(599))
    }

    @Test
    fun `网格列数随窗口宽度分档提升`() {
        assertEquals(3, WideLayoutPolicy.gridColumns(600))
        assertEquals(4, WideLayoutPolicy.gridColumns(840))
        assertEquals(5, WideLayoutPolicy.gridColumns(1080))
        assertEquals(6, WideLayoutPolicy.gridColumns(1200))
    }

    @Test
    fun `超宽窗口列数封顶`() {
        assertEquals(WideLayoutPolicy.MAX_GRID_COLUMNS, WideLayoutPolicy.gridColumns(2400))
    }

    @Test
    fun `窗口尺寸变化后列数重新判定`() {
        // 折叠展开 / 分屏拖动跨过断点时，同一函数随宽度增加而增加，不做启动期缓存
        val compact = WideLayoutPolicy.gridColumns(580)
        val medium = WideLayoutPolicy.gridColumns(650)
        val expanded = WideLayoutPolicy.gridColumns(1000)
        assertEquals(2, compact)
        assertTrue(medium > compact)
        assertTrue(expanded > medium)
    }

    @Test
    fun `单栏内容最大宽度按断点切换`() {
        assertNull(WideLayoutPolicy.contentMaxWidthDp(599))
        assertEquals(640, WideLayoutPolicy.contentMaxWidthDp(600))
        assertEquals(640, WideLayoutPolicy.contentMaxWidthDp(839))
        assertEquals(840, WideLayoutPolicy.contentMaxWidthDp(840))
        assertEquals(840, WideLayoutPolicy.contentMaxWidthDp(1600))
    }

    @Test
    fun `详情双栏只在 Expanded 或横屏平板启用`() {
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 599, heightDp = 400))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 600, heightDp = 400))
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 600, heightDp = 900))
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 839, heightDp = 1000))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 840, heightDp = 1000))
    }

    @Test
    fun `横竖屏切换后详情分栏重新判定`() {
        // 同一块平板旋转：竖屏单栏、横屏双栏
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 760, heightDp = 1024))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 1024, heightDp = 760))
    }
}
