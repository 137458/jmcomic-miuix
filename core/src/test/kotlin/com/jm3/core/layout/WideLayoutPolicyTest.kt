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
    fun `网格列数只按限宽后的内容宽度推导`() {
        // 大屏正文被收窄到 640/840dp，列数必须按收窄后的排布宽度算，
        // 否则窗口越宽列越多、每张卡片越窄（1280dp 曾排出 7 列 116dp 的封面）。
        assertEquals(3, WideLayoutPolicy.gridColumns(600))
        assertEquals(4, WideLayoutPolicy.gridColumns(840))
        assertEquals(4, WideLayoutPolicy.gridColumns(1080))
        assertEquals(4, WideLayoutPolicy.gridColumns(1280))
        assertEquals(4, WideLayoutPolicy.gridColumns(2400))
    }

    @Test
    fun `限宽后的列数封顶`() {
        // 内容宽本身被 840dp 封顶，列数上限只在直接喂排布宽度时生效
        assertEquals(
            WideLayoutPolicy.MAX_GRID_COLUMNS,
            WideLayoutPolicy.gridColumnsForContent(2000),
        )
        assertEquals(4, WideLayoutPolicy.gridColumnsForContent(808))
    }

    @Test
    fun `内容宽度扣掉断点上限`() {
        assertEquals(599, WideLayoutPolicy.contentWidthDp(599))
        assertEquals(600, WideLayoutPolicy.contentWidthDp(600))
        assertEquals(640, WideLayoutPolicy.contentWidthDp(700))
        assertEquals(840, WideLayoutPolicy.contentWidthDp(1280))
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
    fun `详情双栏只在横屏平板或够高的 Expanded 启用`() {
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 599, heightDp = 400))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 600, heightDp = 400))
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 600, heightDp = 900))
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 839, heightDp = 1000))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 840, heightDp = 1000))
        // 横屏一律分栏（左右各占一半仍够放封面），竖屏则要求 840dp 宽且 600dp 高
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 840, heightDp = 412))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 1280, heightDp = 800))
        // 竖屏 Expanded 平板（900x1200）同样分栏：600dp 高度门槛已满足
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 900, heightDp = 1200))
    }

    @Test
    fun `双栏比例按百分比夹取并反算`() {
        assertEquals(0.6f, WideLayoutPolicy.detailSplitFraction(60))
        assertEquals(0.2f, WideLayoutPolicy.detailSplitFraction(5))
        assertEquals(0.8f, WideLayoutPolicy.detailSplitFraction(95))
        assertEquals(60, WideLayoutPolicy.detailSplitPercent(0.6f))
        assertEquals(20, WideLayoutPolicy.detailSplitPercent(0.05f))
    }

    @Test
    fun `拖动双栏时每侧不小于最小栏宽`() {
        // 1280dp 下最小 320dp → 允许区间 0.25..0.75
        assertEquals(0.75f, WideLayoutPolicy.clampDetailSplitFraction(0.9f, 1280))
        assertEquals(0.25f, WideLayoutPolicy.clampDetailSplitFraction(0.1f, 1280))
        assertEquals(0.6f, WideLayoutPolicy.clampDetailSplitFraction(0.6f, 1280))
        // 两栏都放不下最小宽度时退回等分
        assertEquals(0.5f, WideLayoutPolicy.clampDetailSplitFraction(0.9f, 600))
    }

    @Test
    fun `窗口高度不足 600dp 时不出侧栏`() {
        assertFalse(WideLayoutPolicy.supportsNavigationRail(800, 400))
        assertFalse(WideLayoutPolicy.supportsNavigationRail(599, 900))
        assertTrue(WideLayoutPolicy.supportsNavigationRail(600, 600))
        assertTrue(WideLayoutPolicy.supportsNavigationRail(1280, 800))
    }

    @Test
    fun `弹层宽度只在大屏放宽`() {
        assertNull(WideLayoutPolicy.dialogWidthDp(599))
        assertEquals(440, WideLayoutPolicy.dialogWidthDp(600))
        assertEquals(440, WideLayoutPolicy.dialogWidthDp(839))
        assertEquals(520, WideLayoutPolicy.dialogWidthDp(840))
        assertEquals(520, WideLayoutPolicy.dialogWidthDp(1280))
    }

    @Test
    fun `弹层最大高度按窗口高度推导`() {
        // 常规高度取六成；矮横屏改为几乎占满，避免长列表被压成两三行
        assertEquals(480, WideLayoutPolicy.dialogMaxHeightDp(800))
        assertEquals(600, WideLayoutPolicy.dialogMaxHeightDp(1000))
        assertEquals(368, WideLayoutPolicy.dialogMaxHeightDp(400))
        // 尚未测量的窗口（0）不给出约束
        assertEquals(0, WideLayoutPolicy.dialogMaxHeightDp(0))
    }

    @Test
    fun `行卡列表只在内容宽够 840dp 时排两列`() {
        // 手机与中屏内容宽不超过 640dp，恒为单列
        assertEquals(1, WideLayoutPolicy.feedColumns(0))
        assertEquals(1, WideLayoutPolicy.feedColumns(640))
        assertEquals(1, WideLayoutPolicy.feedColumns(839))
        // Expanded 内容宽上限 840dp 起排两列，再宽也最多两列
        assertEquals(2, WideLayoutPolicy.feedColumns(840))
        assertEquals(2, WideLayoutPolicy.feedColumns(1280))
    }

    @Test
    fun `横竖屏切换后详情分栏重新判定`() {
        // 同一块平板旋转：竖屏单栏、横屏双栏
        assertFalse(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 760, heightDp = 1024))
        assertTrue(WideLayoutPolicy.supportsDetailTwoPane(widthDp = 1024, heightDp = 760))
    }
}
