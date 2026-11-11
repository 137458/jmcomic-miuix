package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// 「普通」档即原厂浮动底栏几何：条高 56dp、菜单项 48dp、图标 24dp、标签 10sp、
// 横向 16dp 边距、底距 16dp（无系统手势条时再加 8dp）。库 NavigationBar 自算同一套数值，
// 自绘分支必须与之对齐，否则 34 上换库实现时底栏会突然变高变窄。
// 净空 80dp = 条高 56 + 底距 16 + 呼吸 8，与库的「屏幕底 → 条顶」距离一致。
private val StandardFloating = GlassBottomBarTokens(
    size = BottomBarSize.STANDARD,
    form = BottomBarForm.FLOATING,
    height = 56.dp,
    iconSize = 24.dp,
    labelSize = 10.sp,
    indicatorHeight = 48.dp,
    horizontalMargin = 16.dp,
    innerPadding = 4.dp,
    shadowAlphaLight = 0.1f,
    shadowAlphaDark = 0.2f,
    contentPadding = 80.dp,
)

// 「偏大」档与「普通」档必须拉开可感知的差距：16dp 条高、4dp 图标、3sp 字号。
// 「偏大」档保持原样：它是有意放大的可选观感，不受原厂底栏几何约束。
private val GenerousFloating = GlassBottomBarTokens(
    size = BottomBarSize.GENEROUS,
    form = BottomBarForm.FLOATING,
    height = 72.dp,
    iconSize = 28.dp,
    labelSize = 13.sp,
    indicatorHeight = 64.dp,
    horizontalMargin = 24.dp,
    innerPadding = 4.dp,
    shadowAlphaLight = 0.14f,
    shadowAlphaDark = 0.26f,
    contentPadding = 108.dp,
)

/** 贴底通栏：双端统一走 Miuix 官方 NavigationBar（ItemHeight = 64.dp），无悬浮间隙与投影。 */
private fun docked(size: BottomBarSize): GlassBottomBarTokens {
    val base = when (size) {
        BottomBarSize.STANDARD -> StandardFloating
        BottomBarSize.GENEROUS -> GenerousFloating
    }
    return base.copy(
        form = BottomBarForm.DOCKED,
        height = NavigationBarDefaults.ItemHeight,
        horizontalMargin = 0.dp,
        shadowAlphaLight = 0f,
        shadowAlphaDark = 0f,
        contentPadding = NavigationBarDefaults.ItemHeight,
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
