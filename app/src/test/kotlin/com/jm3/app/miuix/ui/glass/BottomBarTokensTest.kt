package com.jm3.app.miuix.ui.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarTokensTest {

    @Test
    fun `普通档对齐原厂浮动底栏几何`() {
        // 条高 56dp / 菜单项 48dp / 图标 24dp / 标签 10sp / 横向 16dp 边距 / 净空 80dp
        // 这组数值必须与库 NavigationBar 自算的几何一致，34 上换库实现才不会跳变。
        val tokens = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)
        assertEquals(56f, tokens.height.value)
        assertEquals(24f, tokens.iconSize.value)
        assertEquals(10f, tokens.labelSize.value)
        assertEquals(48f, tokens.indicatorHeight.value)
        assertEquals(16f, tokens.horizontalMargin.value)
        assertEquals(4f, tokens.innerPadding.value)
        assertEquals(0.1f, tokens.shadowAlphaLight)
        assertEquals(0.2f, tokens.shadowAlphaDark)
        assertEquals(80f, tokens.contentPadding.value)
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
    fun `四种组合按形态与尺寸档正确生效`() {
        val combos = BottomBarSize.entries.flatMap { size ->
            BottomBarForm.entries.map { form -> bottomBarTokens(size, form) }
        }
        // 悬浮形态：尺寸档决定条高、图标与字号
        val standardFloating = bottomBarTokens(BottomBarSize.STANDARD, BottomBarForm.FLOATING)
        val generousFloating = bottomBarTokens(BottomBarSize.GENEROUS, BottomBarForm.FLOATING)
        assertTrue(generousFloating.height.value > standardFloating.height.value)
        assertTrue(generousFloating.iconSize.value > standardFloating.iconSize.value)
        assertTrue(generousFloating.labelSize.value > standardFloating.labelSize.value)

        // 贴底形态：双端统一走 Miuix NavigationBar，条高与净空固定为 64dp，去掉悬浮边距与投影
        for (size in BottomBarSize.entries) {
            val floating = bottomBarTokens(size, BottomBarForm.FLOATING)
            val docked = bottomBarTokens(size, BottomBarForm.DOCKED)
            assertTrue(floating.horizontalMargin.value > docked.horizontalMargin.value)
            assertTrue(floating.shadowAlphaLight > docked.shadowAlphaLight)
            assertTrue(floating.shadowAlphaDark > docked.shadowAlphaDark)
            assertEquals(64f, docked.height.value)
            assertEquals(64f, docked.contentPadding.value)
            assertTrue(floating.contentPadding.value > docked.contentPadding.value)
        }
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
    fun `形态档在手机上同样可选并按 Miuix NavigationBar 计算净空`() {
        // 形态档没有设备限制：手机同样可选贴底，净空按 Miuix 64dp、无悬浮边距
        val phoneDocked = resolveBottomBarTokens(
            explicitSize = null,
            explicitForm = BottomBarForm.DOCKED,
            widthDp = 400,
        )
        assertEquals(BottomBarForm.DOCKED, phoneDocked.form)
        assertEquals(64f, phoneDocked.height.value)
        assertEquals(64f, phoneDocked.contentPadding.value)
        assertEquals(0f, phoneDocked.horizontalMargin.value)

        // ColorOS 下即使显式偏好是偏大或窗口是大屏，仍收束到标准档位
        val colorOsTablet = resolveBottomBarTokens(
            explicitSize = BottomBarSize.GENEROUS,
            explicitForm = BottomBarForm.FLOATING,
            widthDp = 800,
            skin = UiSkin.COLOROS,
        )
        assertEquals(BottomBarSize.STANDARD, colorOsTablet.size)
        assertEquals(56f, colorOsTablet.height.value)
        assertEquals(80f, colorOsTablet.contentPadding.value)

        val phoneFloating = resolveBottomBarTokens(
            explicitSize = null,
            explicitForm = BottomBarForm.FLOATING,
            widthDp = 400,
        )
        assertEquals(BottomBarForm.FLOATING, phoneFloating.form)
        assertEquals(80f, phoneFloating.contentPadding.value)
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

    @Test
    fun `悬浮底栏在各设备宽度下可用空间严格不超出屏幕`() {
        val testWidths = listOf(320, 360, 390, 412, 600, 800, 1024)
        for (screenW in testWidths) {
            for (size in BottomBarSize.entries) {
                val tokens = bottomBarTokens(size, BottomBarForm.FLOATING)
                val availableWidth = screenW - (tokens.horizontalMargin.value * 2)
                val clampedWidth = availableWidth.coerceAtMost(540f)
                assertTrue(
                    clampedWidth <= screenW,
                    "在屏幕宽度 $screenW 下，底栏宽度 $clampedWidth 不能超出屏幕",
                )
                assertTrue(
                    clampedWidth <= availableWidth,
                    "在屏幕宽度 $screenW 下，底栏宽度 $clampedWidth 不能超出可用空间 $availableWidth",
                )
            }
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
