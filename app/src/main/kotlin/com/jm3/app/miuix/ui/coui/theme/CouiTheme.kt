// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.theme.CouiTypography
import com.jm3.app.miuix.ui.skin.LocalSkin
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * COUI 色板：把应用皮肤色映射到 COUI-Kit 的 [CouiColorScheme]。
 *
 * 颜色真源仍只有皮肤一处：切皮肤或切深浅时，COUI 页面与 Miuix 页面读同一份取值。
 * [CouiColorScheme.background] 是页面底色，[CouiColorScheme.surface]/[CouiColorScheme.container]
 * 是组件表面，[CouiColorScheme.card] 是分组列表卡片；[materialTint] 保持透明，
 * 让浮动栏与弹窗的材质直接采样页面真实颜色，不再叠一层宿主染色。
 */
internal fun couiColorSchemeOf(skinColors: Colors, isDark: Boolean): CouiColorScheme = CouiColorScheme(
    accent = skinColors.primary,
    onAccent = skinColors.onPrimary,
    surface = skinColors.surface,
    container = skinColors.surfaceContainer,
    onSurface = skinColors.onSurface,
    onSurfaceVariant = skinColors.onBackgroundVariant,
    outlineVariant = skinColors.dividerLine,
    accentOnSurface = if (isDark) skinColors.onPrimaryVariant else skinColors.primary,
    menuSurface = skinColors.surfaceContainer,
    checkboxAccent = skinColors.primary,
    menuCheckboxAccent = if (isDark) skinColors.onPrimaryVariant else skinColors.primary,
    background = skinColors.background,
    card = if (isDark) skinColors.surfaceContainerHigh else skinColors.surface,
)

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
 * COUI 页面的主题入口：色板与曲率从 [LocalSkin] 派生，再交给 COUI-Kit 的 CouiTheme。
 *
 * 页面内的 COUI 组件全部读 COUI-Kit 的主题（[CouiColorScheme]/[CouiTypography]），
 * 不再有第二套自研色板；同页复用的 Miuix 组件继续读 MiuixTheme，两者取值同源。
 * 深浅标记由 COUI-Kit 的 CouiTheme 自己下发，这里不再重复 provide。
 */
@Composable
fun CouiTheme(content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val skinColors = if (dark) skin.darkColors else skin.lightColors
    val colorScheme = remember(skin, dark) { couiColorSchemeOf(skinColors, dark) }
    val typography = remember { CouiTypography() }
    CouiKitTheme(
        darkTheme = dark,
        colorScheme = colorScheme,
        typography = typography,
        materialTint = Color.Transparent,
        content = content,
    )
}
