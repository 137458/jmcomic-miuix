// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.theme.CouiTypography
import com.itos.coui.theme.LocalCouiDarkTheme
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
    accentOnSurface = skinColors.primary,
    menuSurface = skinColors.surfaceContainer,
    checkboxAccent = skinColors.primary,
    menuCheckboxAccent = skinColors.primary,
    background = skinColors.background,
    card = if (isDark) skinColors.surfaceContainer else skinColors.surface,
)

/**
 * 自定义文字字阶：COUI-Kit 自己的 Text 只提供固定语义档，
 * 页面里自绘的标题、封面说明与数字仍需要一套集中字阶，避免各页面挑字号。
 * 数字走 tnum，页码与计数在等宽数字下不会左右抖动。
 */
object CouiType {
    val display = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    val topBar = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val section = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.75.sp)
    val caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val tab = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val number = body.copy(fontFeatureSettings = "tnum")
    val cardTitle = body.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    val bodyEmphasis = body.copy(fontWeight = FontWeight.Medium)
    val captionMeta = caption.copy(fontSize = 11.sp)
}

/**
 * COUI 动效参数：push 用接近 ColorOS 的强减速曲线，按压与页签用低阻尼弹簧。
 * 只把数值集中在这里，页面与组件不要各自写字面量。
 */
object CouiMotion {
    const val PushDurationMs = 320
    const val TabCrossfadeMs = 200
    val PushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val PressSpring: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 800f)
    val TabSpring: SpringSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 560f)
    val DialogSpring: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 500f)
}

/**
 * COUI 页面的主题入口：色板与曲率从 [LocalSkin] 派生，再交给 COUI-Kit 的 CouiTheme。
 *
 * 页面内的 COUI 组件全部读 COUI-Kit 的主题（[CouiColorScheme]/[CouiTypography]），
 * 不再有第二套自研色板；同页复用的 Miuix 组件继续读 MiuixTheme，两者取值同源。
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
    ) {
        CompositionLocalProvider(
            LocalCouiDarkTheme provides dark,
            content = content,
        )
    }
}