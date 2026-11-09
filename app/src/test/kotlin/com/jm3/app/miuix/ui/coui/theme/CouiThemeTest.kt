package com.jm3.app.miuix.ui.coui.theme

import com.jm3.app.miuix.ui.skin.SkinShapes
import com.jm3.app.miuix.ui.skin.skinTokensFor
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiThemeTest {

    @Test
    fun `COUI 形状逐项取自皮肤契约`() {
        val skin = skinTokensFor(UiSkin.COLOROS)
        val shapes = couiShapesOf(skin.shapes)

        assertEquals(skin.shapes.cardCorner.value, shapes.card.value)
        assertEquals(skin.shapes.controlCorner.value, shapes.control.value)
        assertEquals(skin.shapes.controlCorner.value, shapes.nested.value)
        assertEquals(skin.shapes.capsuleCorner.value, shapes.pill.value)
        assertEquals(skin.shapes.cardCorner.value, shapes.search.value)
        assertEquals(skin.shapes.sheetCorner.value, shapes.sheet.value)
        // ColorOS 提供独立对话曲面，直接沿用而非另立数值
        assertEquals(26f, shapes.dialog.value)
    }

    @Test
    fun `未提供对话曲面时回落到面板曲率`() {
        val shapes = couiShapesOf(SkinShapes())

        assertEquals(16f, shapes.card.value)
        assertEquals(12f, shapes.control.value)
        assertEquals(999f, shapes.pill.value)
        assertEquals(28f, shapes.sheet.value)
        assertEquals(shapes.sheet.value, shapes.dialog.value)
    }

    @Test
    fun `COUI 色板由皮肤色板派生`() {
        val skin = skinTokensFor(UiSkin.COLOROS)
        val light = couiColorsOf(skin.lightColors, isDark = false)

        assertEquals(skin.lightColors.surfaceContainer, light.card)
        assertEquals(skin.lightColors.surfaceVariant, light.nestedCard)
        assertEquals(skin.lightColors.outline, light.cardBorder)
        assertEquals(skin.lightColors.primary, light.accent)
        assertEquals(skin.lightColors.tertiaryContainer, light.accentContainer)
        assertEquals(skin.lightColors.onSurface, light.textPrimary)
        assertEquals(skin.lightColors.dividerLine, light.divider)
        assertFalse(light.isDark)

        val dark = couiColorsOf(skin.darkColors, isDark = true)
        assertEquals(skin.darkColors.surfaceContainer, dark.card)
        assertEquals(skin.darkColors.background, dark.backgroundTop)
        assertTrue(dark.isDark)
    }
}
