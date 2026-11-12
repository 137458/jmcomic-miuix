// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme
import com.jm3.core.layout.WideLayoutPolicy

/**
 * 详情页双栏之间的分隔条：28dp 触摸区 + 1dp 细线，双击回到默认比例。
 *
 * 视觉只画一条细线，触摸区按手指尺寸放宽，免得必须精确压线才拖得动；
 * 拖动像素按「扣掉分隔条后的可分宽度」换算成比例，夹取与默认值全部走
 * [WideLayoutPolicy]，三个详情页共用同一套手感与边界。
 *
 * 比例由调用方用 rememberSaveable 持有，这里只做换算与回调，不持久化。
 */
@Composable
fun CouiDetailSplitHandle(
    fraction: Float,
    defaultFraction: Float,
    availableWidthDp: Int,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onFractionChange)
    // 比例本身也要读最新值：pointerInput(Unit) 的协程只启动一次，直接闭包捕获会一直拿首帧的旧比例
    val currentFraction by rememberUpdatedState(fraction)
    val resetFraction by rememberUpdatedState(defaultFraction)
    val availableDp by rememberUpdatedState(availableWidthDp)
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(WideLayoutPolicy.DETAIL_DIVIDER_TOUCH_WIDTH_DP.dp)
            .pointerInput(Unit) {
                // 手势协程只启动一次：宽高变化后由 rememberUpdatedState 读最新值，不会被旧闭包锁死
                detectHorizontalDragGestures { changeEvent, dragAmount ->
                    changeEvent.consume()
                    // 可分宽度 = 内容宽扣掉分隔条自身；夹取保证两栏都不窄于最小侧宽
                    val paneWidthPx = with(density) {
                        (availableDp - WideLayoutPolicy.DETAIL_DIVIDER_TOUCH_WIDTH_DP)
                            .coerceAtLeast(1)
                            .dp
                            .toPx()
                    }
                    val dragged = WideLayoutPolicy.clampDetailSplitFraction(
                        fraction = currentFraction + dragAmount / paneWidthPx,
                        availableWidthDp = availableDp,
                    )
                    change(dragged)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { change(resetFraction) })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(CouiTheme.colorScheme.outlineVariant),
        )
    }
}
