package com.jm3.app.miuix.ui.glass

import com.jm3.app.miuix.ui.glass.GlassTier.Blur
import com.jm3.app.miuix.ui.glass.GlassTier.LiquidGlass
import com.jm3.app.miuix.ui.glass.GlassTier.Solid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 三级降级的判定契约：能力缺一级就退一级，绝不停在需要那项能力的那一级；
 * 底栏、顶栏、胶囊、操作胶囊四处必须走同一份判定。
 */
class GlassTierTest {

    @Test
    fun 没有采样源时报什么形态都只能是实色() {
        assertEquals(Solid, resolveGlassTier(hasBackdrop = false, shaderSupported = true))
        assertEquals(
            Solid,
            resolveGlassTier(hasBackdrop = false, shaderSupported = true, requested = FloatingBottomBarMode.Blur),
        )
        assertEquals(
            Solid,
            resolveGlassTier(hasBackdrop = false, shaderSupported = false, requested = FloatingBottomBarMode.None),
        )
    }

    @Test
    fun 液态玻璃要采样源与着色器同时可用() {
        assertEquals(LiquidGlass, resolveGlassTier(hasBackdrop = true, shaderSupported = true))
        // 有采样源但没着色器：退实色，不降到模糊
        assertEquals(Solid, resolveGlassTier(hasBackdrop = true, shaderSupported = false))
    }

    @Test
    fun 模糊降级只要求采样源() {
        assertEquals(
            Blur,
            resolveGlassTier(hasBackdrop = true, shaderSupported = false, requested = FloatingBottomBarMode.Blur),
        )
        // 着色器可用也不把模糊升级成液态玻璃：调用方要的是模糊
        assertEquals(
            Blur,
            resolveGlassTier(hasBackdrop = true, shaderSupported = true, requested = FloatingBottomBarMode.Blur),
        )
    }

    @Test
    fun 请求实色时不会被升级成玻璃() {
        assertEquals(
            Solid,
            resolveGlassTier(hasBackdrop = true, shaderSupported = true, requested = FloatingBottomBarMode.None),
        )
        assertEquals(
            Solid,
            resolveGlassTier(hasBackdrop = true, shaderSupported = false, requested = FloatingBottomBarMode.None),
        )
    }

    @Test
    fun 未指定形态时按能力默认成液态玻璃或模糊() {
        assertEquals(
            FloatingBottomBarMode.LiquidGlass,
            defaultFloatingBottomBarMode(hasBackdrop = true, shaderSupported = true),
        )
        assertEquals(
            FloatingBottomBarMode.Blur,
            defaultFloatingBottomBarMode(hasBackdrop = true, shaderSupported = false),
        )
        assertEquals(
            FloatingBottomBarMode.Blur,
            defaultFloatingBottomBarMode(hasBackdrop = false, shaderSupported = true),
        )
        assertEquals(
            FloatingBottomBarMode.Blur,
            defaultFloatingBottomBarMode(hasBackdrop = false, shaderSupported = false),
        )
    }

    @Test
    fun 只有非实色的两级才需要采样源() {
        assertTrue(LiquidGlass.drawsBackdrop)
        assertTrue(Blur.drawsBackdrop)
        assertFalse(Solid.drawsBackdrop)
    }
}
