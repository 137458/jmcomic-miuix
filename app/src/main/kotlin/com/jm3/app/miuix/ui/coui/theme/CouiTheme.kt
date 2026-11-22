// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import com.jm3.app.miuix.ui.skin.LocalSkin
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.Colors as Coui2Colors
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.theme.darkColorScheme as coui2DarkColorScheme
import io.github.suqi8.coui.kmp.theme.defaultTextStyles
import io.github.suqi8.coui.kmp.theme.lightColorScheme as coui2LightColorScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * COUI 色板：把应用皮肤色映射到 suqi8/coui 的 [Coui2Colors]。
 *
 * 颜色真源仍只有皮肤一处：切皮肤或切深浅时，COUI 页面与 Miuix 页面读同一份取值。
 * [Coui2Colors.backgroundWithCard] 是带卡片页面的底色，[Coui2Colors.surface] 是弹窗/底部面板等浮层表面，
 * [Coui2Colors.card]/[Coui2Colors.cardBackground] 是分组卡片底色。
 */
internal fun couiColorSchemeOf(skinColors: Colors, isDark: Boolean): Coui2Colors {
    val base = if (isDark) coui2DarkColorScheme() else coui2LightColorScheme()
    val primaryText = if (isDark) skinColors.onPrimaryVariant else skinColors.primary
    val themeHalftone = skinColors.primary.copy(alpha = if (isDark) 0.25f else 0.15f)
    val themeFocus = skinColors.primary.copy(alpha = if (isDark) 0.20f else 0.12f)
    val topSurface = if (isDark) skinColors.surfaceContainer else skinColors.surface
    return base.copy(
        primary = skinColors.primary,
        onPrimary = skinColors.onPrimary,
        secondary = themeHalftone,
        surfaceContainerHighest = skinColors.surfaceContainerHighest,
        outline = skinColors.outline,
        sliderBackground = skinColors.sliderBackground,
        backgroundTop = topSurface,
        primaryTextOnPopup = primaryText,
        focus = themeFocus,
        focusOutline = primaryText.copy(alpha = 0.40f),
        primaryText = primaryText,
        labelTheme = skinColors.primary,
        link = skinColors.primary,
        disable = skinColors.primary.copy(alpha = 0.30f),
        containerTheme = skinColors.primaryContainer,
        containerThemeHalftone = themeHalftone,
        textColorHighlight = themeHalftone,
        tips = skinColors.primary,
        additional = skinColors.primary,
        background = if (isDark) skinColors.background else skinColors.surface,
        surface = skinColors.surface,
        backgroundElevated = skinColors.surface,
        backgroundElevatedWithCard = if (isDark) skinColors.surface else skinColors.background,
        backgroundWithCard = skinColors.background,
        surfaceTop = topSurface,
        surfaceWithCard = if (isDark) skinColors.surface else skinColors.background,
        card = if (isDark) skinColors.surfaceContainerHigh else skinColors.surface,
        cardBackground = if (isDark) skinColors.surfaceContainerHigh else skinColors.surfaceContainer,
        cardPressed = if (isDark) skinColors.surfaceContainerHighest else skinColors.surfaceContainerHigh,
        labelPrimary = skinColors.onSurface,
        labelPrimaryVariant = skinColors.onSurfaceSecondary,
        labelSecondary = skinColors.onBackgroundVariant,
        labelSecondaryVariant = skinColors.onSurfaceVariantSummary,
        labelTertiary = skinColors.onSurfaceVariantActions,
        labelOnColor = skinColors.onPrimary,
        disabledNeutral = skinColors.disabledOnSurface,
        hintNeutral = skinColors.onSurfaceVariantActions,
        divider = skinColors.dividerLine,
        mask = skinColors.windowDimming,
        isDark = isDark,
    )
}

/**
 * COUI 统一文案常量：收拢全仓重复硬编码的重试、空态提示语。
 */
object CouiStrings {
    // 重试
    const val Retry = "重试"
    const val Reload = "重新加载"
    const val RetryLater = "请稍后重试"
    const val LoadMoreFailedRetry = "没拿到下一页，可点击重试"
    const val Refreshing = "刷新中"
    const val Refresh = "刷新"

    // 空态
    const val EmptyComments = "暂无评论，来抢沙发"
    const val EmptyChapters = "暂无章节"
    const val EmptyContent = "还没有内容"
    const val EmptyWorks = "这里还没有作品"
    const val EmptySearch = "没有找到作品"
    const val NeedLogin = "需要登录"
    const val LoginExpired = "登录已过期，请重新登录"
    const val OpenLogin = "去登录"

    // 失败态
    const val LoadFailed = "加载失败"
}

/**
 * COUI 动效参数：页签切换的交叉淡出时长集中在这里，页面不要各自写字面量。
 */
object CouiMotion {
    const val TabCrossfadeMs = 200
}

/**
 * COUI 页面的主题入口：色板从 [LocalSkin] 派生，使用 suqi8/coui 的 [COUITheme]
 * （并显式提供 [LocalContentColor]）使指示器、滚动与文本色生效。
 */
@Composable
fun CouiTheme(content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val skinColors = if (dark) skin.darkColors else skin.lightColors
    val coui2Colors = remember(skin, dark) { couiColorSchemeOf(skinColors, dark) }
    val coui2TextStyles = remember { defaultTextStyles() }
    COUITheme(
        colors = coui2Colors,
        textStyles = coui2TextStyles,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides COUITheme.colorScheme.labelPrimary,
            content = content,
        )
    }
}
