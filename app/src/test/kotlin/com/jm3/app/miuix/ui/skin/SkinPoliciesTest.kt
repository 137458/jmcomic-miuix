package com.jm3.app.miuix.ui.skin

import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkinPoliciesTest {

    @Test
    fun hyperOsSupportsDynamicColor() {
        assertTrue(DynamicColorPolicy.supports(UiSkin.HYPEROS))
    }

    @Test
    fun colorOsRejectsDynamicColor() {
        assertFalse(DynamicColorPolicy.supports(UiSkin.COLOROS))
    }

    @Test
    fun hyperOsEffectiveFollowsRequest() {
        assertTrue(DynamicColorPolicy.effective(UiSkin.HYPEROS, requested = true))
        assertFalse(DynamicColorPolicy.effective(UiSkin.HYPEROS, requested = false))
    }

    @Test
    fun colorOsEffectiveStaysOffEvenWhenRequested() {
        assertFalse(DynamicColorPolicy.effective(UiSkin.COLOROS, requested = true))
        assertFalse(DynamicColorPolicy.effective(UiSkin.COLOROS, requested = false))
    }

    @Test
    fun hyperOsKeepsMiuixDefaultTransition() {
        val tokens = skinTokensFor(UiSkin.HYPEROS)
        assertNull(tokens.motion.navMotion)
        assertSame(NavMotion.Default, tokens.motion.navMotionOrDefault())
        assertEquals(16f, tokens.shapes.cardCorner.value)
        assertNull(tokens.shapes.dialogCorner)
    }

    @Test
    fun colorOsTransitionUsesSkinDuration() {
        val tokens = skinTokensFor(UiSkin.COLOROS)
        val programmatic = tokens.motion.navMotionOrDefault().programmatic
        assertTrue(programmatic is NavSettleSpec.Tween)
        assertEquals(tokens.motion.screenTransitionMs, (programmatic as NavSettleSpec.Tween).durationMillis)
    }

    @Test
    fun colorOsHasItsOwnDialogAndSheetCorners() {
        val hyper = skinTokensFor(UiSkin.HYPEROS).shapes
        val color = skinTokensFor(UiSkin.COLOROS).shapes
        assertEquals(28f, hyper.sheetCorner.value)
        assertTrue(color.sheetCorner.value != hyper.sheetCorner.value)
        assertTrue(color.dialogCorner != null)
        assertTrue(color.dialogCorner!!.value != hyper.cardCorner.value)
    }

    @Test
    fun hyperOsSupportsBottomBarSizeSettingAndKeepsRequestedSize() {
        assertTrue(BottomBarSizePolicy.supports(UiSkin.HYPEROS))
        assertEquals(
            BottomBarSize.GENEROUS,
            BottomBarSizePolicy.effective(UiSkin.HYPEROS, BottomBarSize.GENEROUS, widthDp = 400),
        )
        assertEquals(
            BottomBarSize.STANDARD,
            BottomBarSizePolicy.effective(UiSkin.HYPEROS, null, widthDp = 400),
        )
        assertEquals(
            BottomBarSize.GENEROUS,
            BottomBarSizePolicy.effective(UiSkin.HYPEROS, null, widthDp = 800),
        )
    }

    @Test
    fun colorOsDisablesBottomBarSizeSettingAndForcesStandard() {
        assertFalse(BottomBarSizePolicy.supports(UiSkin.COLOROS))
        assertEquals(
            BottomBarSize.STANDARD,
            BottomBarSizePolicy.effective(UiSkin.COLOROS, BottomBarSize.GENEROUS, widthDp = 400),
        )
        assertEquals(
            BottomBarSize.STANDARD,
            BottomBarSizePolicy.effective(UiSkin.COLOROS, null, widthDp = 800),
        )
    }
}
