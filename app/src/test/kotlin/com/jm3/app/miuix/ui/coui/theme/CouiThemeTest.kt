package com.jm3.app.miuix.ui.coui.theme

import com.jm3.app.miuix.ui.skin.skinTokensFor
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals

class CouiThemeTest {

    @Test
    fun `COUI 浅色色板逐项取自皮肤`() {
        val colors = skinTokensFor(UiSkin.COLOROS).lightColors
        val scheme = couiColorSchemeOf(colors, isDark = false)

        assertEquals(colors.primary, scheme.accent)
        assertEquals(colors.onPrimary, scheme.onAccent)
        assertEquals(colors.surface, scheme.surface)
        assertEquals(colors.surfaceContainer, scheme.container)
        assertEquals(colors.onSurface, scheme.onSurface)
        assertEquals(colors.onBackgroundVariant, scheme.onSurfaceVariant)
        assertEquals(colors.dividerLine, scheme.outlineVariant)
        assertEquals(colors.primary, scheme.accentOnSurface)
        assertEquals(colors.surfaceContainer, scheme.menuSurface)
        assertEquals(colors.primary, scheme.checkboxAccent)
        assertEquals(colors.primary, scheme.menuCheckboxAccent)
        assertEquals(colors.background, scheme.background)
        // 浅色下卡片与页面面板同色，靠分组间距区分层级
        assertEquals(colors.surface, scheme.card)
    }

    @Test
    fun `COUI 深色卡片落到容器高亮层`() {
        val colors = skinTokensFor(UiSkin.HYPEROS).darkColors
        val scheme = couiColorSchemeOf(colors, isDark = true)

        // 深色下页面底色已经压到容器层附近，卡片必须再上一档才有可见层级
        assertEquals(colors.surfaceContainerHigh, scheme.card)
        assertEquals(colors.surfaceContainer, scheme.container)
        assertEquals(colors.background, scheme.background)
        assertEquals(colors.primary, scheme.accent)
        assertEquals(colors.onSurface, scheme.onSurface)
        assertEquals(colors.onPrimaryVariant, scheme.accentOnSurface)
        assertEquals(colors.onPrimaryVariant, scheme.menuCheckboxAccent)
    }

    @Test
    fun `深浅切换卡片与次级强调色按模式取值`() {
        val skin = skinTokensFor(UiSkin.COLOROS)
        val light = couiColorSchemeOf(skin.lightColors, isDark = false)
        val dark = couiColorSchemeOf(skin.darkColors, isDark = true)

        assertEquals(skin.darkColors.surfaceContainerHigh, dark.card)
        assertEquals(skin.lightColors.surface, light.card)
        assertEquals(skin.darkColors.primary, dark.accent)
        assertEquals(skin.lightColors.primary, light.accent)
        assertEquals(skin.darkColors.onPrimaryVariant, dark.accentOnSurface)
        assertEquals(skin.lightColors.primary, light.accentOnSurface)
    }
}
