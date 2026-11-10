package com.jm3.app.miuix.ui.skin

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.GlassStyle
import com.jm3.core.settings.UiSkin
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 页面骨架共用的形状 token；消费点按皮肤取值，HyperOS 保持 Miuix 默认。 */
@Immutable
internal data class SkinShapes(
    val cardCorner: Dp = 16.dp,
    val controlCorner: Dp = 12.dp,
    val capsuleCorner: Dp = 999.dp,
    val useSquircle: Boolean = true,
    /** 底部面板圆角；Miuix 默认 28dp。 */
    val sheetCorner: Dp = 28.dp,
    /** 弹窗圆角；null 交给 Miuix 按屏幕圆角推导，HyperOS 保持现状。 */
    val dialogCorner: Dp? = null,
)

/** 动效 token；页面转场与组件动画从同一份弹簧参数取值。 */
@Immutable
internal data class SkinMotion(
    val springDamping: Float = 0.7f,
    val springStiffness: Float = 400f,
    val screenTransitionMs: Int = 300,
    /** 导航转场物理；null 表示沿用 Miuix 默认，HyperOS 保持现状。 */
    val navMotion: NavMotion? = null,
)

/**
 * 一套皮肤的完整取值。页面骨架（底栏 token、宽屏断点、折叠搜索、预测性返回）
 * 本轮不 fork，只在这里取不同参数。
 */
internal data class SkinTokens(
    val id: UiSkin,
    val lightColors: Colors,
    val darkColors: Colors,
    val textStyles: TextStyles,
    val shapes: SkinShapes,
    val motion: SkinMotion,
    val glassStyle: GlassStyle,
)

private val HyperOsSkin = SkinTokens(
    id = UiSkin.HYPEROS,
    lightColors = lightColorScheme(),
    darkColors = darkColorScheme(),
    textStyles = defaultTextStyles(),
    shapes = SkinShapes(
        cardCorner = 16.dp,
        controlCorner = 12.dp,
        capsuleCorner = 999.dp,
        useSquircle = true,
        sheetCorner = 28.dp,
        dialogCorner = null,
    ),
    motion = SkinMotion(
        springDamping = 0.7f,
        springStiffness = 400f,
        screenTransitionMs = 300,
    ),
    glassStyle = GlassStyle.Liquid,
)

/**
 * ColorOS 色板：原生水生蓝主色（Aquamorphic Blue）+ 纯净中性黑灰阶，映射回 Miuix [Colors]，
 * 保证设置页、偏好行等仍使用 Miuix 组件的页面与整机风格一致，彻底告别暗部脏绿。
 */
private val ColorOsLightColors = lightColorScheme(
    primary = Color(0xFF0080FF),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF0070E0),
    onPrimaryVariant = Color(0xFFCCE6FF),
    disabledPrimary = Color(0xFFB3D9FF),
    disabledOnPrimary = Color(0xFFF0F7FF),
    disabledPrimaryButton = Color(0xFFB3D9FF),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = Color(0xFF99CCFF),
    primaryContainer = Color(0xFF0080FF),
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFFE5F2FF),
    onTertiaryContainer = Color(0xFF0080FF),
    tertiaryContainerVariant = Color(0xFFE5F2FF),
    background = Color(0xFFF0F1F2),
    onBackground = Color(0xFF111111),
    onBackgroundVariant = Color(0xFF666666),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color.White,
    onSurfaceSecondary = Color(0xCC111111),
    onSurfaceVariantSummary = Color(0x99111111),
    onSurfaceVariantActions = Color(0x66111111),
    disabledOnSurface = Color(0xFFBBBBBB),
    surfaceContainer = Color.White,
    onSurfaceContainer = Color(0xFF111111),
    onSurfaceContainerVariant = Color(0xFF666666),
    surfaceContainerHigh = Color(0xFFE5E6E8),
    onSurfaceContainerHigh = Color(0xFF555555),
    surfaceContainerHighest = Color(0xFFE5E6E8),
    onSurfaceContainerHighest = Color(0xFF111111),
    outline = Color(0xFFDCDFE3),
    dividerLine = Color(0xFFE0E2E5),
    sliderKeyPoint = Color(0x4D0080FF),
    sliderKeyPointForeground = Color(0xFF0080FF),
)

private val ColorOsDarkColors = darkColorScheme(
    primary = Color(0xFF1A8CFF),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF007BE6),
    onPrimaryVariant = Color(0xFF5C9DFF),
    disabledPrimary = Color(0xFF1E3A5A),
    disabledOnPrimary = Color(0xFF88A8CC),
    disabledPrimaryButton = Color(0xFF1E3A5A),
    disabledOnPrimaryButton = Color(0xFF88A8CC),
    disabledPrimarySlider = Color(0xFF23456B),
    primaryContainer = Color(0xFF1A8CFF),
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFF102840),
    onTertiaryContainer = Color(0xFF5C9DFF),
    tertiaryContainerVariant = Color(0xFF1B3550),
    background = Color(0xFF121212),
    onBackground = Color(0xE6FFFFFF),
    onBackgroundVariant = Color(0x8AFFFFFF),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceSecondary = Color(0xCCFFFFFF),
    onSurfaceVariantSummary = Color(0x80FFFFFF),
    onSurfaceVariantActions = Color(0x66FFFFFF),
    disabledOnSurface = Color(0xFF666666),
    surfaceContainer = Color(0xFF1E1E1E),
    onSurfaceContainer = Color(0xE6FFFFFF),
    onSurfaceContainerVariant = Color(0x8AFFFFFF),
    surfaceContainerHigh = Color(0xFF282828),
    onSurfaceContainerHigh = Color(0xCCFFFFFF),
    surfaceContainerHighest = Color(0xFF333333),
    onSurfaceContainerHighest = Color(0xFFE9E9E9),
    outline = Color(0xFF383838),
    dividerLine = Color(0xFF2C2C2C),
    windowDimming = Color.Black.copy(alpha = 0.6f),
    sliderKeyPoint = Color(0x4D1A8CFF),
    sliderKeyPointForeground = Color(0xFF5C9DFF),
    sliderBackground = Color(0x26FFFFFF),
)

/** ColorOS 标题字重更偏中等，字号沿用 Miuix 默认，避免布局宽度变化。 */
private val ColorOsTextStyles = defaultTextStyles().copy(
    title1 = defaultTextStyles().title1.copy(fontWeight = FontWeight.Medium),
    title2 = defaultTextStyles().title2.copy(fontWeight = FontWeight.Medium),
    title3 = defaultTextStyles().title3.copy(fontWeight = FontWeight.Medium),
    title4 = defaultTextStyles().title4.copy(fontWeight = FontWeight.Medium),
)

private val ColorOsSkin = SkinTokens(
    id = UiSkin.COLOROS,
    lightColors = ColorOsLightColors,
    darkColors = ColorOsDarkColors,
    textStyles = ColorOsTextStyles,
    shapes = SkinShapes(
        cardCorner = 18.dp,
        controlCorner = 14.dp,
        capsuleCorner = 999.dp,
        useSquircle = true,
        sheetCorner = 24.dp,
        dialogCorner = 26.dp,
    ),
    motion = SkinMotion(
        springDamping = 0.78f,
        springStiffness = 460f,
        screenTransitionMs = 280,
        navMotion = NavMotion(
            commit = NavSettleSpec.Spring(1f, 460f),
            cancel = NavSettleSpec.Spring(1f, 460f),
            programmatic = NavSettleSpec.Tween(280, FastOutSlowInEasing),
        ),
    ),
    glassStyle = GlassStyle.Frosted,
)

/** 皮肤取值入口；同一档位返回同一实例，避免组合期反复构造颜色与字体。 */
internal fun skinTokensFor(id: UiSkin): SkinTokens = when (id) {
    UiSkin.HYPEROS -> HyperOsSkin
    UiSkin.COLOROS -> ColorOsSkin
}

/** 当前皮肤；默认液态玻璃 HyperOS，与旧行为一致。 */
internal val LocalSkin = staticCompositionLocalOf { HyperOsSkin }
