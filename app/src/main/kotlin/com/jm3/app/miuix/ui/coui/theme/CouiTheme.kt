// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.ui.skin.SkinShapes
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * COUI（ColorOS 17 取向）固定色板。
 *
 * 不走动态取色：ColorOS 的绿色是品牌固定值，动态取色会让底栏、选中胶囊与按钮一起漂移，
 * 反而失去系统辨识度。深浅由 MiuixTheme 的 surface 亮度判定，沿用应用现有主题切换。
 */
@Immutable
data class CouiColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val nestedCard: Color,
    val cardBorder: Color,
    val barSurface: Color,
    val topBarSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val error: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val divider: Color,
    val searchField: Color,
    val searchBorder: Color,
    val scrim: Color,
    val ambientGreen: Color,
    val ambientBlue: Color,
)

/**
 * 由当前皮肤色板派生 COUI 色板，COUI 不再持有第二份颜色真源。
 *
 * 底色、卡片、分隔线与文字色全部映射自皮肤 [Colors]：切皮肤或切深浅时，
 * COUI 页面与设置页、偏好行读的是同一份取值。只有 [CouiColors.ambientBlue]
 * 因为皮肤契约里没有蓝色，按深浅保留字面量。
 */
internal fun couiColorsOf(skinColors: Colors, isDark: Boolean): CouiColors = CouiColors(
    isDark = isDark,
    backgroundTop = skinColors.background,
    backgroundBottom = skinColors.surface,
    card = skinColors.surfaceContainer,
    nestedCard = skinColors.surfaceVariant,
    cardBorder = skinColors.outline,
    barSurface = skinColors.surfaceVariant,
    topBarSurface = skinColors.surfaceVariant,
    textPrimary = skinColors.onSurface,
    textSecondary = skinColors.onBackgroundVariant,
    textTertiary = skinColors.onSurfaceVariantActions,
    error = skinColors.error,
    accent = skinColors.primary,
    accentContainer = skinColors.tertiaryContainer,
    onAccent = skinColors.onPrimary,
    divider = skinColors.dividerLine,
    searchField = skinColors.surfaceContainer,
    searchBorder = skinColors.outline,
    scrim = Color.Black.copy(alpha = 0.35f),
    ambientGreen = skinColors.primary,
    ambientBlue = if (isDark) Color(0xFF6E98FF) else Color(0xFF4C7DFF),
)

/**
 * COUI 字阶：只保留标题、分组、正文、辅助、按钮、页签六档，避免页面各自挑字号。
 * 数字走 tnum，页码/计数/时长在等宽数字下不会左右抖动。
 */
object CouiType {
    val display = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    )
    val topBar = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val title = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val section = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.75.sp,
    )
    val caption = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
        color = Color.Unspecified,
    )
    val button = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
    val tab = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    val number = body.copy(fontFeatureSettings = "tnum")

    /** 卡内标题：列表卡固定 13/17 中粗，页面不再写字面量。 */
    val cardTitle = body.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)

    /** 强调正文：字阶不变，只加粗一档。 */
    val bodyEmphasis = body.copy(fontWeight = FontWeight.Medium)

    /** 细则：卡内副标题、标签这类 11sp 小字。 */
    val captionMeta = caption.copy(fontSize = 11.sp)
}

/**
 * COUI 组件取用的形状档位。全部映射自皮肤契约 [SkinShapes]，
 * 组件里不再出现第二份圆角数值。
 */
@Immutable
internal data class CouiShapeSpec(
    val card: Dp,
    val nested: Dp,
    val control: Dp,
    val pill: Dp,
    val search: Dp,
    val sheet: Dp,
    val dialog: Dp,
)

/** 把皮肤形状映射成 COUI 档位：卡片与外框取卡片曲率，行内控件取控件曲率。 */
internal fun couiShapesOf(shapes: SkinShapes): CouiShapeSpec = CouiShapeSpec(
    card = shapes.cardCorner,
    nested = shapes.controlCorner,
    control = shapes.controlCorner,
    pill = shapes.capsuleCorner,
    search = shapes.cardCorner,
    sheet = shapes.sheetCorner,
    dialog = shapes.dialogCorner ?: shapes.sheetCorner,
)

/**
 * COUI 动效参数：push 用接近 iOS/ColorOS 的强减速曲线，按压与页签用低阻尼弹簧。
 * 只把数值集中在这里，页面与组件不要各自写字面量。
 */
object CouiMotion {
    const val PushDurationMs = 320
    const val TabCrossfadeMs = 200
    const val TopBarFadeMs = 240
    val PushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val PressSpring: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 800f)
    val TabSpring: SpringSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 560f)
    val TabColorSpring: SpringSpec<Color> = spring(dampingRatio = 0.78f, stiffness = 560f)
    val ListSpring: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 400f)
    val ListSizeSpring: SpringSpec<IntSize> = spring(dampingRatio = 0.85f, stiffness = 400f)
    val DialogSpring: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 500f)
    val SheetSpring: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 450f)
    val SheetOffsetSpring: SpringSpec<IntOffset> = spring(dampingRatio = 0.85f, stiffness = 450f)
}

val LocalCouiColors = staticCompositionLocalOf { couiColorsOf(lightColorScheme(), isDark = false) }

/** 当前皮肤的 COUI 形状档位；由 [CouiTheme] 一次性算出，组件只读。 */
internal val LocalCouiShapes = staticCompositionLocalOf { couiShapesOf(SkinShapes()) }

/**
 * COUI 页面的主题入口。色板与形状都从 [LocalSkin] 派生，不覆盖 MiuixTheme：
 * 页面内 Miuix 组件（过渡期复用的设置行等）继续读皮肤色，视觉不会互相打架。
 */
@Composable
fun CouiTheme(content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val colors = remember(skin, dark) {
        couiColorsOf(if (dark) skin.darkColors else skin.lightColors, isDark = dark)
    }
    val shapes = remember(skin) { couiShapesOf(skin.shapes) }
    CompositionLocalProvider(
        LocalCouiColors provides colors,
        LocalCouiShapes provides shapes,
        content = content,
    )
}
