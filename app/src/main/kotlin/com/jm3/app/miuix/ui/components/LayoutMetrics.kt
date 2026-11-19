package com.jm3.app.miuix.ui.components

/** 间距与弹窗尺寸的统一取值：留白、触区、弹窗上限都从这里取，避免各页自己写 dp。 */

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.pinnedLineHeight
import top.yukonga.miuix.kmp.layout.DialogDefaults

internal val SectionHorizontalPadding: Dp = 12.dp

/**
 * 无外观膨胀的最小触区包装：内层紧凑按钮保持原尺寸，外层至少撑到 [minSize]×[minSize]，
 * 仅在子控件未消费的边缘留白补接点击（启用状态为真时触发），避免灰底胶囊膨胀或重复朗读。
 */
@Composable
internal fun MinTouchTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minSize: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestEnabled by rememberUpdatedState(enabled)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (latestEnabled) latestOnClick()
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 纯文字分区头的水平起点：与 [SmallTitle] 内建的 28dp 文字基线对齐。
 *
 * 卡片正文的起点是 [SectionHorizontalPadding] 12dp + 卡片内建 16dp = 28dp；
 * 没有卡片内衬的裸文字头必须直接取本值，不能再按 12dp 排。
 */
internal val SectionTextStartPadding: Dp = SectionHorizontalPadding + 16.dp

/**
 * 弹窗宽度上限：大屏按断点放宽（440/520dp），手机返回组件库默认 420dp。
 *
 * 组件库默认值固定 420dp，1280dp 窗口下弹窗只占屏宽三分之一，标题与操作按钮都被挤压。
 * 只给 [OverlayDialog] 用：抽屉的库默认是 640dp，已经宽于本值，再收紧反而是倒退。
 */
@Composable
internal fun overlayDialogMaxWidth(): Dp =
    LocalWideLayout.current.dialogWidth ?: DialogDefaults.MaxWidth

/**
 * 弹层内容高度上限：不超过窗口推导值，也不超过调用方原本的保守上限。
 *
 * 两个方向都必要：竖屏手机窗口高 800dp 时推导值 480dp 会放宽原有 320/420/440dp 的观感，
 * 而 800x400dp 矮横屏推导值只有 368dp，写死的高度会把列表压出窗口、末尾几行点不到。
 */
@Composable
internal fun overlayMaxHeight(preferred: Dp): Dp =
    minOf(preferred, LocalWideLayout.current.dialogMaxHeight ?: preferred)

/**
 * 大屏字号档：宽 840dp 以上把标题类文字升 2sp，行高同步 +3sp。
 *
 * 1280dp 窗口下网格卡片宽到 196dp，标题仍是手机的 16sp，标题与卡片不成比例；
 * 只抬标题层级、正文保持原样，避免整页变松。行高必须跟字号一起抬：
 * 调用方会传固定标题行高（[WorkCard] 的 pinnedLineHeight），只放大字号会把文字裁掉。
 */
@Composable
internal fun largeScreenTitleStyle(base: TextStyle): TextStyle {
    if (!LocalWideLayout.current.largeTypography) return base
    // 只在 Sp 单位上做加法：Em 或 Unspecified 保持原样，避免把未指定的行高算成 NaN
    return base.copy(
        fontSize = if (base.fontSize.isSp) (base.fontSize.value + 2f).sp else base.fontSize,
        lineHeight = if (base.lineHeight.isSp) (base.lineHeight.value + 3f).sp else base.lineHeight,
    )
}
