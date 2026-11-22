package com.jm3.app.miuix.ui.coui.theme

import com.jm3.app.miuix.ui.skin.skinTokensFor
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiThemeTest {

    @Test
    fun `COUI 浅色色板逐项取自皮肤`() {
        val colors = skinTokensFor(UiSkin.COLOROS).lightColors
        val scheme = couiColorSchemeOf(colors, isDark = false)

        assertFalse(scheme.isDark)
        assertEquals(colors.primary, scheme.primary)
        assertEquals(colors.onPrimary, scheme.onPrimary)
        assertEquals(colors.primary, scheme.primaryText)
        assertEquals(colors.primary, scheme.labelTheme)
        assertEquals(colors.primary, scheme.link)
        assertEquals(colors.primaryContainer, scheme.containerTheme)
        assertEquals(colors.primary.copy(alpha = 0.15f), scheme.secondary)
        assertEquals(colors.surface, scheme.background)
        assertEquals(colors.background, scheme.backgroundWithCard)
        assertEquals(colors.surface, scheme.surface)
        // 浅色下卡片与页面面板同色，靠分组间距区分层级
        assertEquals(colors.surface, scheme.card)
        assertEquals(colors.surfaceContainer, scheme.cardBackground)
        assertEquals(colors.surfaceContainerHigh, scheme.cardPressed)
        assertEquals(colors.onSurface, scheme.labelPrimary)
        assertEquals(colors.onBackgroundVariant, scheme.labelSecondary)
        assertEquals(colors.onSurfaceVariantSummary, scheme.labelSecondaryVariant)
        assertEquals(colors.onSurfaceVariantActions, scheme.labelTertiary)
        assertEquals(colors.dividerLine, scheme.divider)
    }

    @Test
    fun `COUI 深色卡片落到容器高亮层`() {
        val colors = skinTokensFor(UiSkin.HYPEROS).darkColors
        val scheme = couiColorSchemeOf(colors, isDark = true)

        assertTrue(scheme.isDark)
        // 深色下页面底色已经压到容器层附近，卡片必须再上一档才有可见层级
        assertEquals(colors.surfaceContainerHigh, scheme.card)
        assertEquals(colors.surfaceContainerHigh, scheme.cardBackground)
        assertEquals(colors.surfaceContainerHighest, scheme.cardPressed)
        assertEquals(colors.background, scheme.background)
        assertEquals(colors.background, scheme.backgroundWithCard)
        assertEquals(colors.surface, scheme.surface)
        assertEquals(colors.primary, scheme.primary)
        assertEquals(colors.onPrimaryVariant, scheme.primaryText)
        assertEquals(colors.primary.copy(alpha = 0.25f), scheme.secondary)
        assertEquals(colors.onSurface, scheme.labelPrimary)
        assertEquals(colors.onBackgroundVariant, scheme.labelSecondary)
        assertEquals(colors.dividerLine, scheme.divider)
    }

    @Test
    fun `深浅切换卡片与次级强调色按模式取值`() {
        val skin = skinTokensFor(UiSkin.COLOROS)
        val light = couiColorSchemeOf(skin.lightColors, isDark = false)
        val dark = couiColorSchemeOf(skin.darkColors, isDark = true)

        assertFalse(light.isDark)
        assertTrue(dark.isDark)
        assertEquals(skin.darkColors.surfaceContainerHigh, dark.card)
        assertEquals(skin.lightColors.surface, light.card)
        assertEquals(skin.darkColors.primary, dark.primary)
        assertEquals(skin.lightColors.primary, light.primary)
        assertEquals(skin.darkColors.onPrimaryVariant, dark.primaryText)
        assertEquals(skin.lightColors.primary, light.primaryText)
        assertEquals(skin.darkColors.primaryContainer, dark.containerTheme)
        assertEquals(skin.lightColors.primaryContainer, light.containerTheme)
    }
}
