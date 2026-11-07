package com.jm3.app.miuix.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassSkinTest {

    @Test
    fun `ColorOS 配方显式关闭折射与 vibrancy 并改为均匀模糊`() {
        for (role in GlassRole.values()) {
            val spec = glassRoleSpec(role, GlassStyle.Frosted)
            assertEquals(0f, spec.lensHeightDp, "$role 不应保留折射带")
            assertEquals(0f, spec.lensAmountDp, "$role 不应保留折射量")
            assertFalse(spec.vibrancy, "$role 不应开启 vibrancy")
            assertEquals(spec.blurTierBlurDp, spec.liquidBlurDp, "$role 应使用均匀磨砂半径")
            assertEquals(spec.blurScrimAlpha, spec.liquidScrimAlpha, "$role 底衬应走材质混色")
        }
    }

    @Test
    fun `ColorOS 均匀磨砂不依赖运行时着色器`() {
        assertEquals(
            GlassTier.Blur,
            resolveGlassTier(
                hasBackdrop = true,
                shaderSupported = false,
                requested = FloatingBottomBarMode.LiquidGlass,
                style = GlassStyle.Frosted,
            ),
        )
        assertEquals(
            GlassTier.Blur,
            resolveGlassTier(
                hasBackdrop = true,
                shaderSupported = true,
                requested = FloatingBottomBarMode.LiquidGlass,
                style = GlassStyle.Frosted,
            ),
        )
    }

    @Test
    fun `ColorOS 降级三档不变`() {
        assertEquals(
            GlassTier.Solid,
            resolveGlassTier(
                hasBackdrop = false,
                shaderSupported = true,
                style = GlassStyle.Frosted,
            ),
        )
        val blur = resolveGlassSurface(GlassRole.BottomBar, GlassTier.Blur, GlassStyle.Frosted)
        assertTrue(blur.drawsBackdrop)
        assertEquals(25f, blur.blurRadiusDp)
        assertEquals(0f, blur.lensRefractionAmountDp)
        assertFalse(blur.vibrancy)
        assertEquals(0.65f, blur.scrimAlpha)

        val solid = resolveGlassSurface(GlassRole.BottomBar, GlassTier.Solid, GlassStyle.Frosted)
        assertFalse(solid.drawsBackdrop)
        assertEquals(1f, solid.scrimAlpha)
    }

    @Test
    fun `HyperOS 配方与旧角色表逐项一致`() {
        for (role in GlassRole.values()) {
            assertEquals(glassRoleSpec(role), glassRoleSpec(role, GlassStyle.Liquid))
            assertEquals(
                resolveGlassSurface(role, GlassTier.LiquidGlass),
                resolveGlassSurface(role, GlassTier.LiquidGlass, GlassStyle.Liquid),
            )
        }
        val bottomBar = resolveGlassSurface(GlassRole.BottomBar, GlassTier.LiquidGlass, GlassStyle.Liquid)
        assertEquals(4f, bottomBar.blurRadiusDp)
        assertEquals(24f, bottomBar.lensRefractionAmountDp)
        assertTrue(bottomBar.vibrancy)
        assertEquals(0.75f, bottomBar.highlightAlpha)
    }
}
