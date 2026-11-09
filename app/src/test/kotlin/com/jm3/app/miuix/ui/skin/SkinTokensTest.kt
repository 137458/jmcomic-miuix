package com.jm3.app.miuix.ui.skin

import com.jm3.app.miuix.ui.glass.GlassStyle
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

class SkinTokensTest {

    @Test
    fun `默认皮肤逐项沿用现行 Miuix 配色与字体`() {
        val skin = skinTokensFor(UiSkin.HYPEROS)
        val light = lightColorScheme()
        val dark = darkColorScheme()

        assertEquals(UiSkin.HYPEROS, skin.id)
        assertEquals(light.primary, skin.lightColors.primary)
        assertEquals(light.primaryContainer, skin.lightColors.primaryContainer)
        assertEquals(light.background, skin.lightColors.background)
        assertEquals(light.surface, skin.lightColors.surface)
        assertEquals(light.surfaceContainerHigh, skin.lightColors.surfaceContainerHigh)
        assertEquals(light.onSurface, skin.lightColors.onSurface)
        assertEquals(dark.primary, skin.darkColors.primary)
        assertEquals(dark.background, skin.darkColors.background)
        assertEquals(dark.surfaceContainerHighest, skin.darkColors.surfaceContainerHighest)
        assertEquals(defaultTextStyles().main.fontSize, skin.textStyles.main.fontSize)
        assertEquals(defaultTextStyles().title2.fontSize, skin.textStyles.title2.fontSize)
        assertEquals(GlassStyle.Liquid, skin.glassStyle)
    }

    @Test
    fun `ColorOS 皮肤使用独立绿色板并改为均匀磨砂`() {
        val hyper = skinTokensFor(UiSkin.HYPEROS)
        val colorOs = skinTokensFor(UiSkin.COLOROS)

        assertEquals(UiSkin.COLOROS, colorOs.id)
        assertNotEquals(hyper.lightColors.primary, colorOs.lightColors.primary)
        assertNotEquals(hyper.darkColors.primary, colorOs.darkColors.primary)
        assertNotEquals(hyper.lightColors.primaryContainer, colorOs.lightColors.primaryContainer)
        assertEquals(GlassStyle.Frosted, colorOs.glassStyle)
    }

    @Test
    fun `同一档位重复取值返回同一实例`() {
        assertSame(skinTokensFor(UiSkin.HYPEROS), skinTokensFor(UiSkin.HYPEROS))
        assertSame(skinTokensFor(UiSkin.COLOROS), skinTokensFor(UiSkin.COLOROS))
    }
}
