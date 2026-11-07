package com.jm3.app.miuix.ui.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarTokensTest {

    @Test
    fun `普通档映射改前原值`() {
        val tokens = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)
        assertEquals(64f, tokens.height.value)
        assertEquals(24f, tokens.iconSize.value)
        assertEquals(11f, tokens.labelSize.value)
        assertEquals(56f, tokens.indicatorHeight.value)
        assertEquals(24f, tokens.horizontalMargin.value)
        assertEquals(4f, tokens.innerPadding.value)
        assertEquals(0.1f, tokens.shadowAlphaLight)
        assertEquals(0.2f, tokens.shadowAlphaDark)
        assertEquals(100f, tokens.contentPadding.value)
        assertTrue(tokens.floating)
        val (topLeft, bottomLeft) = cornerRadii(tokens)
        assertEquals(50f, topLeft)
        assertEquals(50f, bottomLeft)
    }

    @Test
    fun `偏大档映射方案一数值`() {
        val tokens = bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING)
        assertEquals(68f, tokens.height.value)
        assertEquals(26f, tokens.iconSize.value)
        assertEquals(12f, tokens.labelSize.value)
        assertEquals(60f, tokens.indicatorHeight.value)
        assertEquals(0.14f, tokens.shadowAlphaLight)
        assertEquals(0.26f, tokens.shadowAlphaDark)
        assertEquals(104f, tokens.contentPadding.value)
        val (topLeft, bottomLeft) = cornerRadii(tokens)
        assertEquals(50f, topLeft)
        assertEquals(50f, bottomLeft)
    }

    @Test
    fun `贴底档去掉悬浮边距与投影并按条高算净空`() {
        for (size in BottomBarSize.entries) {
            val tokens = bottomBarTokens(size, BottomBarForm.DOCKED)
            assertFalse(tokens.floating)
            assertEquals(0f, tokens.horizontalMargin.value)
            assertEquals(0f, tokens.shadowAlphaLight)
            assertEquals(0f, tokens.shadowAlphaDark)
            assertEquals(tokens.height.value, tokens.contentPadding.value)
            val (topLeft, bottomLeft) = cornerRadii(tokens)
            assertEquals(24f, topLeft)
            assertEquals(0f, bottomLeft)
        }
    }

    @Test
    fun `未设置时默认值按窗口断点解析`() {
        val phone = resolveBottomBarTokens(explicitSize = null, explicitForm = null, widthDp = 400)
        assertEquals(BottomBarSize.STANDARD, phone.size)
        assertEquals(BottomBarForm.FLOATING, phone.form)

        val tablet = resolveBottomBarTokens(explicitSize = null, explicitForm = null, widthDp = 800)
        assertEquals(BottomBarSize.GENEROUS, tablet.size)
        assertEquals(BottomBarForm.FLOATING, tablet.form)

        // 用户显式改过之后不再被断点覆盖
        val tabletDocked = resolveBottomBarTokens(
            explicitSize = BottomBarSize.STANDARD,
            explicitForm = BottomBarForm.DOCKED,
            widthDp = 800,
        )
        assertEquals(BottomBarSize.STANDARD, tabletDocked.size)
        assertEquals(BottomBarForm.DOCKED, tabletDocked.form)
    }

    @Test
    fun `液态玻璃折射参数与采样不变量不随档位变化`() {
        for (size in BottomBarSize.entries) for (form in BottomBarForm.entries) {
            val tokens = bottomBarTokens(size, form)
            val plan = resolveGlassSurface(GlassRole.BottomBar, GlassTier.LiquidGlass)
            assertEquals(24f, plan.lensRefractionAmountDp)
            assertTrue(
                plan.effectPaddingDp >= plan.lensRefractionAmountDp,
                "$size/$form 采样余量必须覆盖折射量",
            )
            assertTrue(
                tokens.contentPadding.value >= tokens.height.value,
                "$size/$form 内容净空不能小于条高",
            )
        }
    }

    /** 用实际绘制的形状生成 outline，验证真实生效的圆角，而不是未被绘制消费的 token。 */
    private fun cornerRadii(tokens: GlassBottomBarTokens): Pair<Float, Float> {
        val outline = bottomBarShape(tokens).createOutline(
            size = Size(100f, 100f),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f),
        )
        val roundRect = (outline as Outline.Rounded).roundRect
        return roundRect.topLeftCornerRadius.x to roundRect.bottomLeftCornerRadius.x
    }
}
