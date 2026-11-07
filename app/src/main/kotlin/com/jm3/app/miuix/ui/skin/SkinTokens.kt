package com.jm3.app.miuix.ui.skin

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.GlassStyle
import com.jm3.core.settings.UiSkin
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 页面骨架共用的形状 token；第一阶段只暴露契约，不 fork 现有圆角实现。 */
@Immutable
internal data class SkinShapes(
    val cardCorner: Dp = 16.dp,
    val controlCorner: Dp = 12.dp,
    val capsuleCorner: Dp = 999.dp,
    val useSquircle: Boolean = true,
)

/** 动效 token；页面转场与组件动画从同一份弹簧参数取值。 */
@Immutable
internal data class SkinMotion(
    val springDamping: Float = 0.7f,
    val springStiffness: Float = 400f,
    val screenTransitionMs: Int = 300,
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
    ),
    motion = SkinMotion(
        springDamping = 0.7f,
        springStiffness = 400f,
        screenTransitionMs = 300,
    ),
    glassStyle = GlassStyle.Liquid,
)

/**
 * ColorOS 色板：绿色主色 + 中性微绿的容器层次，映射回 Miuix [Colors]，
 * 保证设置页、偏好行等仍使用 Miuix 组件的页面与整机风格一致。
 */
private val ColorOsLightColors = lightColorScheme(
    primary = Color(0xFF1DA26B),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF1DA26B),
    onPrimaryVariant = Color(0xFFB7E6D0),
    disabledPrimary = Color(0xFFBFE6D5),
    disabledOnPrimary = Color(0xFFF3FBF7),
    disabledPrimaryButton = Color(0xFFBFE6D5),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = Color(0xFFAEDFCB),
    primaryContainer = Color(0xFF3FCB8B),
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFFE7F7EF),
    onTertiaryContainer = Color(0xFF1DA26B),
    tertiaryContainerVariant = Color(0xFFE7F7EF),
    background = Color(0xFFF8F9F8),
    onBackground = Color(0xFF111413),
    onBackgroundVariant = Color(0xFF7D8A83),
    surface = Color(0xFFF2F5F3),
    onSurface = Color(0xFF111413),
    surfaceVariant = Color.White,
    onSurfaceSecondary = Color(0xCC111413),
    onSurfaceVariantSummary = Color(0x99111413),
    onSurfaceVariantActions = Color(0x66111413),
    disabledOnSurface = Color(0xFFB5BCB7),
    surfaceContainer = Color.White,
    onSurfaceContainer = Color(0xFF111413),
    onSurfaceContainerVariant = Color(0xFF8D9691),
    surfaceContainerHigh = Color(0xFFE8EEEA),
    onSurfaceContainerHigh = Color(0xFF68736D),
    surfaceContainerHighest = Color(0xFFE8EEEA),
    onSurfaceContainerHighest = Color(0xFF111413),
    outline = Color(0xFFD7E0DA),
    dividerLine = Color(0xFFE1E9E3),
    sliderKeyPoint = Color(0x4D8AA79B),
    sliderKeyPointForeground = Color(0xFF48CE90),
)

private val ColorOsDarkColors = darkColorScheme(
    primary = Color(0xFF34C98B),
    onPrimary = Color.White,
    primaryVariant = Color(0xFF2AB076),
    onPrimaryVariant = Color(0xFF9EE5C6),
    disabledPrimary = Color(0xFF24473A),
    disabledOnPrimary = Color(0xFF6D8A7D),
    disabledPrimaryButton = Color(0xFF24473A),
    disabledOnPrimaryButton = Color(0xFF6D8A7D),
    disabledPrimarySlider = Color(0xFF3C5A4C),
    primaryContainer = Color(0xFF2CA979),
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFF20392F),
    onTertiaryContainer = Color(0xFF5BE0A5),
    tertiaryContainerVariant = Color(0xFF3A4C43),
    background = Color(0xFF1B1E1C),
    onBackground = Color(0xE6FFFFFF),
    onBackgroundVariant = Color(0xFF7C8982),
    surface = Color.Black,
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF1B1E1C),
    onSurfaceSecondary = Color(0xCCFFFFFF),
    onSurfaceVariantSummary = Color(0x80FFFFFF),
    onSurfaceVariantActions = Color(0x66FFFFFF),
    disabledOnSurface = Color(0xFF666666),
    surfaceContainer = Color(0xFF1E2220),
    onSurfaceContainer = Color(0xE6FFFFFF),
    onSurfaceContainerVariant = Color(0xFF8E9892),
    surfaceContainerHigh = Color(0xFF2A2F2C),
    onSurfaceContainerHigh = Color(0xFF97A19B),
    surfaceContainerHighest = Color(0xFF2D322F),
    onSurfaceContainerHighest = Color(0xFFE9E9E9),
    outline = Color(0xFF3E4541),
    dividerLine = Color(0xFF333A36),
    windowDimming = Color.Black.copy(alpha = 0.6f),
    sliderKeyPoint = Color(0x4D6F8A7E),
    sliderKeyPointForeground = Color(0xFF3FD79A),
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
    ),
    motion = SkinMotion(
        springDamping = 0.78f,
        springStiffness = 460f,
        screenTransitionMs = 280,
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
