package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.jm3.core.layout.WideLayoutPolicy
import kotlin.math.floor

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

    /** 限宽后真正可用的内容宽度：列数与卡片尺寸都以它为基准。 */
    val contentWidthDp: Int = WideLayoutPolicy.contentWidthDp(widthDp)

    val gridColumns: Int = WideLayoutPolicy.gridColumns(widthDp)

    /** 行卡列表的分栏列数：内容宽够 840dp 才排两列，手机恒为单列。 */
    val feedColumns: Int = WideLayoutPolicy.feedColumns(contentWidthDp)
    val twoPaneDetail: Boolean = WideLayoutPolicy.supportsDetailTwoPane(widthDp, heightDp)

    /** 侧栏可用的宽高双条件：矮横屏即使够宽也不出侧栏。 */
    val supportsRail: Boolean = WideLayoutPolicy.supportsNavigationRail(widthDp, heightDp)

    /** 大屏放大档：840dp 起标题与控件允许加一档，手机断点以下恒为 false。 */
    val largeTypography: Boolean = widthDp >= WideLayoutPolicy.EXPANDED_MIN_WIDTH_DP

    /** 弹层宽度：手机为 null 表示沿用组件库默认宽度。 */
    val dialogWidth: Dp? = WideLayoutPolicy.dialogWidthDp(widthDp)?.dp

    /** 弹层最大高度：null 表示不额外约束（手机竖屏与默认值场景）。 */
    val dialogMaxHeight: Dp? = WideLayoutPolicy.dialogMaxHeightDp(heightDp)
        .takeIf { it > 0 }
        ?.dp

    /** 双栏详情默认比例：0.2..0.8，两栏都放不下最小宽度时退回等分。 */
    val defaultDetailSplitFraction: Float = WideLayoutPolicy.clampDetailSplitFraction(
        WideLayoutPolicy.detailSplitFraction(WideLayoutPolicy.DETAIL_SPLIT_PERCENT_DEFAULT),
        contentWidthDp,
    )

    /** 限宽后的左右留白：窗口超宽时把内容收进可读宽度并居中。 */
    val readableHorizontalPadding: Dp
        get() = contentMaxWidth
            ?.let { ((widthDp - it.value) / 2).coerceAtLeast(0f).dp }
            ?: 0.dp
}

/** 由组合根在窗口尺寸变化时重建；默认值代表手机断点以下（不限宽、两列、单栏）。 */
val LocalWideLayout = staticCompositionLocalOf { WideLayout(widthDp = 0, heightDp = 0) }

/**
 * 当前窗口尺寸（dp）：断点判定只允许读这一处。
 *
 * 直接取 [LocalWindowInfo] 已折算好的 [androidx.compose.ui.platform.WindowInfo.containerDpSize]，
 * 不用 `Configuration.screenWidthDp/screenHeightDp`：后者按 targetSdk 追加或剔除系统栏留白，
 * 分屏、自由窗口与折叠屏展开态下与真实窗口不一致，600/840dp 断点会误判。
 * 容器尺寸是可观察状态，分屏、旋转与折叠展开后自动重组。
 */
@Composable
fun rememberWindowDpSize(): DpSize = LocalWindowInfo.current.containerDpSize

/**
 * 断点比较用的整数 dp：向下取整。
 *
 * 600/840dp 是「至少这么宽」的下限，599.6dp 不能被四舍五入成 600 提前进入大屏布局。
 */
internal fun Dp.toLayoutDpInt(): Int = floor(value).toInt().coerceAtLeast(0)

/**
 * 单栏内容限宽：大屏通过左右对称留白把内容收进可读宽度并居中，手机断点以下留白为 0。
 *
 * 用内边距而不是改测量约束，调用方的 `fillMaxWidth()` 语义保持不变，
 * 列表、滚动与手势区域也不会因为限宽而错位。
 */
@Composable
fun Modifier.readableWidth(): Modifier =
    this.padding(horizontal = LocalWideLayout.current.readableHorizontalPadding)

/**
 * 顶栏 / 悬浮动作栏等「看上去属于正文」的元素用它对齐内容列。
 *
 * 与 [readableWidth] 同一份留白来源，因此大屏上标题、返回键与正文左缘始终共线；
 * 满宽背景（玻璃采样层）不要套它，否则模糊边缘会跟着内容列收缩。
 */
@Composable
fun Modifier.alignToReadableContent(): Modifier =
    this.padding(horizontal = LocalWideLayout.current.readableHorizontalPadding)

/**
 * 侧栏占用横向空间后，重新以「内容盒宽度」为基准推导布局参数。
 *
 * 限宽留白按窗口宽计算会让正文在有侧栏时右偏，因此主框架在侧栏内侧再提供一份
 * 以剩余宽度为窗口宽的 [WideLayout]；页面侧无需知道侧栏是否存在。
 */
@Composable
fun railAwareWideLayout(availableWidthDp: Int): WideLayout {
    val window = LocalWideLayout.current
    if (availableWidthDp <= 0 || availableWidthDp == window.widthDp) return window
    return window.copy(widthDp = availableWidthDp)
}
