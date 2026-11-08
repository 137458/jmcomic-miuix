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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

object CouiPalette {
    val Light = CouiColors(
        isDark = false,
        backgroundTop = Color(0xFFF1F4F6),
        backgroundBottom = Color(0xFFE9EEF2),
        card = Color(0xFFFFFFFF),
        nestedCard = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFE6EBE8),
        barSurface = Color(0xFFFBFCFB),
        topBarSurface = Color(0xFFFBFCFB),
        textPrimary = Color(0xFF111413),
        textSecondary = Color(0xFF7D8A83),
        textTertiary = Color(0xFFA6B0AA),
        accent = Color(0xFF1DA26B),
        accentContainer = Color(0xFFE7F7EF),
        onAccent = Color(0xFFFFFFFF),
        divider = Color(0xFFE6EBE8),
        searchField = Color(0xFFFFFFFF),
        searchBorder = Color(0xFFDDE5E0),
        scrim = Color(0x59000000),
        ambientGreen = Color(0xFF1DA26B),
        ambientBlue = Color(0xFF4C7DFF),
    )

    val Dark = CouiColors(
        isDark = true,
        backgroundTop = Color(0xFF0E1110),
        backgroundBottom = Color(0xFF141817),
        card = Color(0xFF1B201D),
        nestedCard = Color(0xFF1B201D),
        cardBorder = Color(0xFF2A322D),
        barSurface = Color(0xFF141817),
        topBarSurface = Color(0xFF141817),
        textPrimary = Color(0xFFF1F1F1),
        textSecondary = Color(0xFF8C9A93),
        textTertiary = Color(0xFF66736C),
        accent = Color(0xFF34C98B),
        accentContainer = Color(0xFF17362A),
        onAccent = Color(0xFF06291C),
        divider = Color(0xFF2A322D),
        searchField = Color(0xFF1B201D),
        searchBorder = Color(0xFF2A322D),
        scrim = Color(0x59000000),
        ambientGreen = Color(0xFF34C98B),
        ambientBlue = Color(0xFF6E98FF),
    )

    fun forDark(dark: Boolean): CouiColors = if (dark) Dark else Light
}

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
}

object CouiShapes {
    val cardRadius: Dp = 20.dp
    val nestedCardRadius: Dp = 16.dp
    val dialogRadius: Dp = 28.dp
    val sheetRadius: Dp = 28.dp
    val controlRadius: Dp = 12.dp
    val searchRadius: Dp = 20.dp
    val pillRadius: Dp = 16.dp
}

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
    val ListSpring: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 400f)
    val DialogSpring: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 500f)
    val SheetSpring: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 450f)
}

val LocalCouiColors = staticCompositionLocalOf { CouiPalette.Light }

/**
 * COUI 页面的主题入口。只提供 COUI 自有色板，不覆盖 MiuixTheme：
 * 页面内 Miuix 组件（过渡期复用的设置行等）继续读 COLOROS 皮肤色，视觉不会互相打架。
 */
@Composable
fun CouiTheme(content: @Composable () -> Unit) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val colors = remember(dark) { CouiPalette.forDark(dark) }
    CompositionLocalProvider(LocalCouiColors provides colors, content = content)
}
