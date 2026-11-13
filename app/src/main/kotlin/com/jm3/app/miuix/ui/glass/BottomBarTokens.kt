package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.bottomBarContentPaddingDp
import com.jm3.app.miuix.ui.skin.BottomBarSizePolicy
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.UiSkin
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults

/**
 * 底栏尺寸 token：尺寸档与形态档在组合期一次性解析成这份纯数值，
 * 绘制与测量阶段只读，不在滚动或切换过程中逐帧重算。
 *
 * 形态档只改外层占位（贴底/悬浮、横向边距、投影、内容净空），
 * 液态玻璃折射参数与 `effectPadding ≥ lensAmount` 不变量保持原样。
 */
internal data class GlassBottomBarTokens(
    val size: BottomBarSize,
    val form: BottomBarForm,
    val height: Dp,
    val iconSize: Dp,
    val labelSize: TextUnit,
    val indicatorHeight: Dp,
    val horizontalMargin: Dp,
    val innerPadding: Dp,
    val shadowAlphaLight: Float,
    val shadowAlphaDark: Float,
    /** 交给 `LocalFloatingBarContentPadding` 的底部净空（不含系统导航栏 inset）。 */
    val contentPadding: Dp,
) {
    val floating: Boolean get() = form == BottomBarForm.FLOATING
}

private val DockedTopCorner = 24.dp

/**
 * 悬浮底栏的视觉度量 → 完整 token。
 *
 * 净空不在这里落数字，统一向 [bottomBarContentPaddingDp] 取，传入的就是本条实际绘制高度：
 * 条高与净空因此只有一处来源，不会各自写一个数而漂移。
 */
private fun floatingTokens(
    size: BottomBarSize,
    height: Dp,
    iconSize: Dp,
    labelSize: TextUnit,
    indicatorHeight: Dp,
    horizontalMargin: Dp,
    shadowAlphaLight: Float,
    shadowAlphaDark: Float,
): GlassBottomBarTokens = GlassBottomBarTokens(
    size = size,
    form = BottomBarForm.FLOATING,
    height = height,
    iconSize = iconSize,
    labelSize = labelSize,
    indicatorHeight = indicatorHeight,
    horizontalMargin = horizontalMargin,
    innerPadding = 4.dp,
    shadowAlphaLight = shadowAlphaLight,
    shadowAlphaDark = shadowAlphaDark,
    contentPadding = bottomBarContentPaddingDp(
        heightDp = height.value,
        size = size,
        form = BottomBarForm.FLOATING,
    ).dp,
)

// 「普通」档即原厂浮动底栏几何：条高 56dp、菜单项 48dp、图标 24dp、标签 10sp、
// 横向 16dp 边距、底距 16dp（无系统手势条时再加 8dp）。库 NavigationBar 自算同一套数值，
// 自绘分支必须与之对齐，否则 34 上换库实现时底栏会突然变高变窄。
// 净空 80dp = 条高 56 + 底距 16 + 呼吸 8，公式在 [bottomBarContentPaddingDp]，与库的「屏幕底 → 条顶」距离一致。
private val StandardFloating = floatingTokens(
    size = BottomBarSize.STANDARD,
    height = 56.dp,
    iconSize = 24.dp,
    labelSize = 10.sp,
    indicatorHeight = 48.dp,
    horizontalMargin = 16.dp,
    shadowAlphaLight = 0.1f,
    shadowAlphaDark = 0.2f,
)

// 「偏大」档与「普通」档必须拉开可感知的差距：16dp 条高、4dp 图标、3sp 字号。
// 「偏大」档保持原样：它是有意放大的可选观感，不受原厂底栏几何约束；
// 净空 108dp = 条高 72 + 底距 24 + 呼吸 12，底距与呼吸按普通档 1.5 倍放大。
private val GenerousFloating = floatingTokens(
    size = BottomBarSize.GENEROUS,
    height = 72.dp,
    iconSize = 28.dp,
    labelSize = 13.sp,
    indicatorHeight = 64.dp,
    horizontalMargin = 24.dp,
    shadowAlphaLight = 0.14f,
    shadowAlphaDark = 0.26f,
)

/** 贴底通栏：双端统一走 Miuix 官方 NavigationBar（ItemHeight = 64.dp），无悬浮间隙与投影。 */
private fun docked(size: BottomBarSize): GlassBottomBarTokens {
    val base = when (size) {
        BottomBarSize.STANDARD -> StandardFloating
        BottomBarSize.GENEROUS -> GenerousFloating
    }
    val height = NavigationBarDefaults.ItemHeight
    return base.copy(
        form = BottomBarForm.DOCKED,
        height = height,
        horizontalMargin = 0.dp,
        shadowAlphaLight = 0f,
        shadowAlphaDark = 0f,
        // 贴底形态没有底距与呼吸，净空退回条高本身（64dp），推导仍在 BottomSpacing 一侧。
        contentPadding = bottomBarContentPaddingDp(
            heightDp = height.value,
            size = size,
            form = BottomBarForm.DOCKED,
        ).dp,
    )
}

internal fun bottomBarTokens(size: BottomBarSize, form: BottomBarForm): GlassBottomBarTokens =
    when (form) {
        BottomBarForm.FLOATING -> when (size) {
            BottomBarSize.STANDARD -> StandardFloating
            BottomBarSize.GENEROUS -> GenerousFloating
        }

        BottomBarForm.DOCKED -> docked(size)
    }

/** 组合期解析：ColorOS 固定走标准档位；形态档未设置即悬浮。 */
internal fun resolveBottomBarTokens(
    explicitSize: BottomBarSize?,
    explicitForm: BottomBarForm?,
    widthDp: Int,
    skin: UiSkin = UiSkin.HYPEROS,
): GlassBottomBarTokens {
    val size = BottomBarSizePolicy.effective(skin, explicitSize, widthDp)
    val form = BottomBarPreferencePolicy.resolveForm(explicitForm)
    return bottomBarTokens(size, form)
}

internal fun bottomBarShape(tokens: GlassBottomBarTokens): Shape =
    if (tokens.floating) CircleShape else RoundedCornerShape(topStart = DockedTopCorner, topEnd = DockedTopCorner)

internal val LocalGlassBottomBarTokens = staticCompositionLocalOf {
    StandardFloating
}
