package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize

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

    /** 胶囊圆角：悬浮为半高，贴底只圆上两角。 */
    val cornerRadius: Dp get() = if (floating) height / 2 else DockedTopCorner
}

private val DockedTopCorner = 24.dp

private val StandardFloating = GlassBottomBarTokens(
    size = BottomBarSize.STANDARD,
    form = BottomBarForm.FLOATING,
    height = 64.dp,
    iconSize = 24.dp,
    labelSize = 11.sp,
    indicatorHeight = 56.dp,
    horizontalMargin = 24.dp,
    innerPadding = 4.dp,
    shadowAlphaLight = 0.1f,
    shadowAlphaDark = 0.2f,
    contentPadding = 100.dp,
)

private val GenerousFloating = GlassBottomBarTokens(
    size = BottomBarSize.GENEROUS,
    form = BottomBarForm.FLOATING,
    height = 68.dp,
    iconSize = 26.dp,
    labelSize = 12.sp,
    indicatorHeight = 60.dp,
    horizontalMargin = 24.dp,
    innerPadding = 4.dp,
    shadowAlphaLight = 0.14f,
    shadowAlphaDark = 0.26f,
    contentPadding = 104.dp,
)

/** 贴底通栏：横向撑满、无悬浮间隙与投影；净空只留条高（导航栏 inset 由外层 Scaffold 计入）。 */
private fun docked(size: BottomBarSize): GlassBottomBarTokens {
    val base = when (size) {
        BottomBarSize.STANDARD -> StandardFloating
        BottomBarSize.GENEROUS -> GenerousFloating
    }
    return base.copy(
        form = BottomBarForm.DOCKED,
        horizontalMargin = 0.dp,
        shadowAlphaLight = 0f,
        shadowAlphaDark = 0f,
        contentPadding = base.height,
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

/** 组合期解析：未显式设置时按窗口断点取默认档。 */
internal fun resolveBottomBarTokens(
    explicitSize: BottomBarSize?,
    explicitForm: BottomBarForm?,
    widthDp: Int,
): GlassBottomBarTokens {
    val size = BottomBarPreferencePolicy.resolveSize(explicitSize, widthDp)
    val form = BottomBarPreferencePolicy.resolveForm(explicitForm, widthDp)
    return bottomBarTokens(size, form)
}

internal fun bottomBarShape(tokens: GlassBottomBarTokens): Shape =
    if (tokens.floating) CircleShape else RoundedCornerShape(topStart = DockedTopCorner, topEnd = DockedTopCorner)

internal val LocalGlassBottomBarTokens = staticCompositionLocalOf {
    StandardFloating
}
