package com.jm3.app.miuix.ui.glass

import com.jm3.app.miuix.ui.effect.resolveShaderCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 顶栏玻璃计划：档位只判一次，底衬与模糊叠层的不透明度都来自角色表与唯一常量。
 *
 * 这些断言同时钉住「绘制路径不得自行推导透明度」：任何一处再把 0.3f 或角色表数值抄进 Composable，
 * 都会与这里对不上。
 */
class TopBarGlassTest {

    @Test
    fun `液态档顶栏交出采样源并沿用角色表的零底衬`() {
        val glass = resolveTopBarGlass(hasBackdrop = true, shaderSupported = true, style = GlassStyle.Liquid)
        val plan = resolveGlassSurface(GlassRole.TopAppBar, GlassTier.LiquidGlass, GlassStyle.Liquid)

        assertEquals(GlassTier.LiquidGlass, glass.tier)
        assertTrue(glass.drawsBackdrop)
        assertEquals(plan.scrimAlpha, glass.scrimColorAlpha)
        assertEquals(0f, glass.scrimColorAlpha)
    }

    @Test
    fun `没有采样源时顶栏退实色且底衬不透明`() {
        val glass = resolveTopBarGlass(hasBackdrop = false, shaderSupported = true, style = GlassStyle.Liquid)

        assertEquals(GlassTier.Solid, glass.tier)
        assertFalse(glass.drawsBackdrop)
        assertEquals(1f, glass.scrimColorAlpha)
    }

    @Test
    fun `ColorOS 配方只降到模糊档且底衬取自模糊档配方`() {
        val glass = resolveTopBarGlass(hasBackdrop = true, shaderSupported = false, style = GlassStyle.Frosted)
        val plan = resolveGlassSurface(GlassRole.TopAppBar, GlassTier.Blur, GlassStyle.Frosted)

        assertEquals(GlassTier.Blur, glass.tier)
        assertTrue(glass.drawsBackdrop)
        assertEquals(plan.scrimAlpha, glass.scrimColorAlpha)
    }

    @Test
    fun `每个组合的底衬都等于角色表且模糊叠层透明度只有唯一常量`() {
        for (hasBackdrop in listOf(true, false)) {
            for (shaderSupported in listOf(true, false)) {
                for (style in listOf(GlassStyle.Liquid, GlassStyle.Frosted)) {
                    val glass = resolveTopBarGlass(hasBackdrop, shaderSupported, style)
                    val plan = resolveGlassSurface(GlassRole.TopAppBar, glass.tier, style)

                    assertEquals(
                        plan.scrimAlpha,
                        glass.scrimColorAlpha,
                        "hasBackdrop=$hasBackdrop, shader=$shaderSupported, style=$style 的底衬必须来自角色表",
                    )
                    assertEquals(
                        plan.drawsBackdrop,
                        glass.drawsBackdrop,
                        "hasBackdrop=$hasBackdrop, shader=$shaderSupported, style=$style 的采样结论必须来自降级矩阵",
                    )
                    assertEquals(TOP_BAR_BLUR_TINT_ALPHA, glass.blurTintAlpha)
                }
            }
        }
        assertEquals(0.3f, TOP_BAR_BLUR_TINT_ALPHA)
    }

    @Test
    fun `顶栏消费共享能力入口的判定结果`() {
        val shaderSupported = resolveShaderCapability(33).runtimeShaderSupported
        val glass = resolveTopBarGlass(hasBackdrop = true, shaderSupported = shaderSupported, style = GlassStyle.Liquid)

        assertEquals(GlassTier.LiquidGlass, glass.tier)
    }
}
