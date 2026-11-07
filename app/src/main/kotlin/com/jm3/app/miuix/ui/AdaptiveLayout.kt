package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.core.layout.WideLayoutPolicy

/**
 * 当前窗口的宽高（dp）及其派生布局参数。
 *
 * 判定全部委托给 [WideLayoutPolicy]；这里只把结果保留成组合期常量，
 * 供绘制与测量阶段直接读取，不在列表滚动或测量热路径里重算。
 */
@Immutable
data class WideLayout(
    val widthDp: Int,
    val heightDp: Int,
) {
    val contentMaxWidth: Dp? = WideLayoutPolicy.contentMaxWidthDp(widthDp)?.dp
    val gridColumns: Int = WideLayoutPolicy.gridColumns(widthDp)
    val twoPaneDetail: Boolean = WideLayoutPolicy.supportsDetailTwoPane(widthDp, heightDp)

    /** 限宽后的左右留白：窗口超宽时把内容收进可读宽度并居中。 */
    val readableHorizontalPadding: Dp
        get() = contentMaxWidth
            ?.let { ((widthDp - it.value) / 2).coerceAtLeast(0f).dp }
            ?: 0.dp
}

/** 由组合根在窗口尺寸变化时重建；默认值代表手机断点以下（不限宽、两列、单栏）。 */
val LocalWideLayout = staticCompositionLocalOf { WideLayout(widthDp = 0, heightDp = 0) }

/**
 * 单栏内容限宽：大屏通过左右对称留白把内容收进可读宽度并居中，手机断点以下留白为 0。
 *
 * 用内边距而不是改测量约束，调用方的 `fillMaxWidth()` 语义保持不变，
 * 列表、滚动与手势区域也不会因为限宽而错位。
 */
@Composable
fun Modifier.readableWidth(): Modifier =
    this.padding(horizontal = LocalWideLayout.current.readableHorizontalPadding)
