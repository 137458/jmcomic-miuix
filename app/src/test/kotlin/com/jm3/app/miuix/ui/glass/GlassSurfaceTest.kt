package com.jm3.app.miuix.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassSurfaceTest {

    @Test
    fun `液态玻璃档各表面各自的理想形态`() {
        val bottomBar = resolveGlassSurface(GlassRole.BottomBar, GlassTier.LiquidGlass)
        assertTrue(bottomBar.drawsBackdrop)
        assertEquals(4f, bottomBar.blurRadiusDp)
        assertEquals(24f, bottomBar.lensRefractionHeightDp)
        assertEquals(24f, bottomBar.lensRefractionAmountDp)
        assertTrue(bottomBar.vibrancy)
        assertEquals(0.75f, bottomBar.highlightAlpha)
        assertEquals(0.4f, bottomBar.scrimAlpha)
        assertEquals(40f, bottomBar.effectPaddingDp)

        val fab = resolveGlassSurface(GlassRole.FloatingActionBar, GlassTier.LiquidGlass)
        assertEquals(12f, fab.lensRefractionHeightDp)
        assertEquals(12f, fab.lensRefractionAmountDp)
        assertEquals(1f, fab.highlightAlpha)
        assertEquals(0.4f, fab.scrimAlpha)

        val capsule = resolveGlassSurface(GlassRole.Capsule, GlassTier.LiquidGlass)
        assertEquals(8f, capsule.blurRadiusDp)
        assertEquals(12f, capsule.lensRefractionHeightDp)
        assertEquals(10f, capsule.lensRefractionAmountDp)
        assertTrue(capsule.vibrancy)
        assertEquals(0.55f, capsule.scrimAlpha)
        assertEquals(32f, capsule.effectPaddingDp)

        val snackbar = resolveGlassSurface(GlassRole.Snackbar, GlassTier.LiquidGlass)
        assertEquals(20f, snackbar.blurRadiusDp)
        assertEquals(0f, snackbar.lensRefractionAmountDp)
        assertEquals(0f, snackbar.highlightAlpha)
        assertTrue(snackbar.vibrancy)
        assertEquals(0.68f, snackbar.scrimAlpha)

        val toolbar = resolveGlassSurface(GlassRole.Toolbar, GlassTier.LiquidGlass)
        assertEquals(22f, toolbar.blurRadiusDp)
        assertEquals(0f, toolbar.lensRefractionAmountDp)
        assertFalse(toolbar.vibrancy)
        assertEquals(1f, toolbar.highlightAlpha)
        assertEquals(0.78f, toolbar.scrimAlpha)

        val topBar = resolveGlassSurface(GlassRole.TopAppBar, GlassTier.LiquidGlass)
        assertEquals(10f, topBar.blurRadiusDp)
        assertTrue(topBar.drawsBackdrop)
        assertEquals(0f, topBar.scrimAlpha)

        val searchField = resolveGlassSurface(GlassRole.SearchField, GlassTier.LiquidGlass)
        assertTrue(searchField.drawsBackdrop)
        assertEquals(10f, searchField.blurRadiusDp, "与顶栏渐进模糊同半径，避免胶囊边缘跳变")
        assertEquals(12f, searchField.lensRefractionHeightDp)
        assertEquals(12f, searchField.lensRefractionAmountDp)
        assertEquals(0.5f, searchField.highlightAlpha)
        assertEquals(0.72f, searchField.scrimAlpha)
        assertTrue(searchField.effectPaddingDp >= searchField.lensRefractionAmountDp)
        assertEquals(18f, resolveGlassSurface(GlassRole.SearchField, GlassTier.Blur).blurRadiusDp)
        assertEquals(0.88f, resolveGlassSurface(GlassRole.SearchField, GlassTier.Blur).scrimAlpha)
        assertEquals(1f, resolveGlassSurface(GlassRole.SearchField, GlassTier.Solid).scrimAlpha)
        assertFalse(resolveGlassSurface(GlassRole.SearchField, GlassTier.Solid).drawsBackdrop)
    }

    @Test
    fun `模糊档只留采样与磨砂`() {
        for (role in GlassRole.values()) {
            val plan = resolveGlassSurface(role, GlassTier.Blur)
            assertTrue(plan.drawsBackdrop, "$role 模糊档仍要采样")
            assertTrue(plan.blurRadiusDp > 0f, "$role 模糊档要有磨砂半径")
            assertEquals(0f, plan.lensRefractionAmountDp, "$role 模糊档不折射")
            assertEquals(0f, plan.lensRefractionHeightDp, "$role 模糊档不折射")
            assertEquals(0f, plan.highlightAlpha, "$role 模糊档不画边缘高光")
            assertFalse(plan.vibrancy, "$role 模糊档不提饱和")
            assertFalse(plan.border, "$role 模糊档不描边")
            assertEquals(0f, plan.effectPaddingDp, "$role 模糊档不需要采样余量")
        }

        assertEquals(25f, resolveGlassSurface(GlassRole.BottomBar, GlassTier.Blur).blurRadiusDp)
        assertEquals(25f, resolveGlassSurface(GlassRole.FloatingActionBar, GlassTier.Blur).blurRadiusDp)
        assertEquals(8f, resolveGlassSurface(GlassRole.Capsule, GlassTier.Blur).blurRadiusDp)
        assertEquals(20f, resolveGlassSurface(GlassRole.Snackbar, GlassTier.Blur).blurRadiusDp)
    }

    @Test
    fun `实色档不采样只铺底衬`() {
        val bottomBar = resolveGlassSurface(GlassRole.BottomBar, GlassTier.Solid)
        assertFalse(bottomBar.drawsBackdrop)
        assertEquals(0f, bottomBar.blurRadiusDp)
        assertEquals(1f, bottomBar.scrimAlpha)
        assertFalse(bottomBar.border)

        val fab = resolveGlassSurface(GlassRole.FloatingActionBar, GlassTier.Solid)
        assertEquals(1f, fab.scrimAlpha)
        assertTrue(fab.border, "只有操作胶囊的实色兜底描边")

        assertEquals(0.75f, resolveGlassSurface(GlassRole.Capsule, GlassTier.Solid).scrimAlpha)
        assertEquals(0.95f, resolveGlassSurface(GlassRole.Snackbar, GlassTier.Solid).scrimAlpha)
        assertEquals(1f, resolveGlassSurface(GlassRole.Toolbar, GlassTier.Solid).scrimAlpha)
        assertEquals(1f, resolveGlassSurface(GlassRole.TopAppBar, GlassTier.Solid).scrimAlpha)
    }

    @Test
    fun `折射采样余量永远不小于折射量`() {
        val snug = GlassRoleSpec(
            effectPaddingDp = 8f,
            liquidBlurDp = 4f,
            blurTierBlurDp = 25f,
            lensHeightDp = 12f,
            lensAmountDp = 32f,
            highlightAlpha = 1f,
            liquidScrimAlpha = 0.4f,
            blurScrimAlpha = 0.65f,
            solidScrimAlpha = 1f,
        )
        assertEquals(32f, resolveGlassSurface(snug, GlassTier.LiquidGlass).effectPaddingDp)

        val roomy = snug.copy(effectPaddingDp = 40f)
        assertEquals(40f, resolveGlassSurface(roomy, GlassTier.LiquidGlass).effectPaddingDp)
    }

    @Test
    fun `任意角色与档位的组合都自洽`() {
        for (role in GlassRole.values()) for (tier in GlassTier.values()) {
            val plan = resolveGlassSurface(role, tier)
            assertEquals(tier.drawsBackdrop, plan.drawsBackdrop, "$role/$tier 是否采样由档位决定")
            assertTrue(plan.scrimAlpha in 0f..1f, "$role/$tier 底衬不透明度越界")
            assertTrue(plan.highlightAlpha in 0f..1f, "$role/$tier 高光不透明度越界")
            assertTrue(plan.effectPaddingDp >= 0f, "$role/$tier 采样余量为负")
            assertTrue(plan.lensRefractionAmountDp >= 0f, "$role/$tier 折射量为负")
            assertTrue(plan.lensRefractionHeightDp >= 0f, "$role/$tier 折射带为负")
        }
    }
}
