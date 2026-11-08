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
    fun `偏大档数值与普通档拉开可感知差距`() {
        val standard = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)
        val tokens = bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING)
        assertEquals(72f, tokens.height.value)
        assertEquals(28f, tokens.iconSize.value)
        assertEquals(13f, tokens.labelSize.value)
        assertEquals(64f, tokens.indicatorHeight.value)
        assertEquals(0.14f, tokens.shadowAlphaLight)
        assertEquals(0.26f, tokens.shadowAlphaDark)
        assertEquals(108f, tokens.contentPadding.value)
        // 与普通档必须拉开肉眼可辨的差距；普通档自身数值不受影响
        assertTrue(tokens.height.value - standard.height.value >= 8f)
        assertTrue(tokens.iconSize.value - standard.iconSize.value >= 4f)
        assertTrue(tokens.labelSize.value - standard.labelSize.value >= 2f)
        val (topLeft, bottomLeft) = cornerRadii(tokens)
        assertEquals(50f, topLeft)
        assertEquals(50f, bottomLeft)
    }

    @Test
    fun `四种组合逐一可区分`() {
        val combos = BottomBarSize.entries.flatMap { size ->
            BottomBarForm.entries.map { form -> bottomBarTokens(size, form) }
        }
        // 尺寸档：同一形态下条高、图标、字号逐项不同
        for (form in BottomBarForm.entries) {
            val standard = bottomBarTokens(BottomBarSize.STANDARD, form)
            val generous = bottomBarTokens(BottomBarSize.GENEROUS, form)
            assertTrue(generous.height.value > standard.height.value)
            assertTrue(generous.iconSize.value > standard.iconSize.value)
            assertTrue(generous.labelSize.value > standard.labelSize.value)
        }
        // 形态档：悬浮有横向边距与投影，贴底没有；净空也随形态不同
        for (size in BottomBarSize.entries) {
            val floating = bottomBarTokens(size, BottomBarForm.FLOATING)
            val docked = bottomBarTokens(size, BottomBarForm.DOCKED)
            assertTrue(floating.horizontalMargin.value > docked.horizontalMargin.value)
            assertTrue(floating.shadowAlphaLight > docked.shadowAlphaLight)
            assertTrue(floating.shadowAlphaDark > docked.shadowAlphaDark)
            assertEquals(floating.height, docked.height)
            assertTrue(floating.contentPadding.value > docked.contentPadding.value)
        }
        // 四份 token 必须各互不相同，避免两档退化成同一套几何
        assertEquals(4, combos.toSet().size)
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

        // 普通档等价自适应：平板上解析为偏大；显式形态选择仍按用户选择
        val tabletDocked = resolveBottomBarTokens(
            explicitSize = BottomBarSize.STANDARD,
            explicitForm = BottomBarForm.DOCKED,
            widthDp = 800,
        )
        assertEquals(BottomBarSize.GENEROUS, tabletDocked.size)
        assertEquals(BottomBarForm.DOCKED, tabletDocked.form)
    }

    @Test
    fun `形态档在手机上同样可选并按 token 计算净空`() {
        // 形态档没有设备限制：手机同样可选贴底，净空按条高、无悬浮边距
        val phoneDocked = resolveBottomBarTokens(
            explicitSize = null,
            explicitForm = BottomBarForm.DOCKED,
            widthDp = 400,
        )
        assertEquals(BottomBarForm.DOCKED, phoneDocked.form)
        assertEquals(64f, phoneDocked.height.value)
        assertEquals(64f, phoneDocked.contentPadding.value)
        assertEquals(0f, phoneDocked.horizontalMargin.value)

        val phoneFloating = resolveBottomBarTokens(
            explicitSize = null,
            explicitForm = BottomBarForm.FLOATING,
            widthDp = 400,
        )
        assertEquals(BottomBarForm.FLOATING, phoneFloating.form)
        assertEquals(100f, phoneFloating.contentPadding.value)
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
