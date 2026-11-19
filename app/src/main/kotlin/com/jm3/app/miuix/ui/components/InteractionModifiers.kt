package com.jm3.app.miuix.ui.components

/** 交互修饰器与骨架屏：按压缩放、选中震动、微光、边缘淡化、封面骨架。 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 按压微缩放：叠加弹簧回弹（阻尼 0.55 / 刚度 900）与可选轻触觉反馈。
 * 用 pointerInput 旁路监听而不消费事件，因此不会吞掉 Card/列表项自身的点击。
 */
@Composable
fun Modifier.pressScale(scaleDown: Float = 0.96f, hapticFeedback: Boolean = true): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(hapticFeedback) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                if (hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/** 切换类控件（Tab/筛选项）的轻触觉反馈；返回可直接调用的回调。 */
@Composable
fun rememberSelectionHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** 微光掠过一轮的时长：慢一档像呼吸，快一档像卡顿。 */
private const val SHIMMER_PERIOD_MS = 1200

/**
 * 微光骨架屏动效：封面图片未完成加载时展现平滑对角线微光掠过，消除纯灰块突兀感。
 *
 * 微光色取主题里高一层表面色的半透明，周期由 SHIMMER_PERIOD_MS 统一：调用点只回答
 * 「要不要闪」，不再各自挑颜色与节奏。
 */
@Composable
fun Modifier.shimmer(visible: Boolean = true): Modifier {
    if (!visible) return this
    val shimmerColor = MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return this.drawBehind {
        val width = size.width
        val height = size.height
        val totalDistance = width + height
        val currentOffset = totalDistance * progress
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor,
                Color.Transparent,
            ),
            start = Offset(currentOffset - height, 0f),
            end = Offset(currentOffset, height),
        )
        drawRect(brush = brush)
    }
}

/** 边缘羽化带的默认宽度：与胶囊条右缘渐隐同一档，滚动区边缘不再出现硬切线。 */
internal val EdgeFadeWidth = 24.dp

/**
 * 边缘羽化底座：离屏合成后用 DstIn 遮罩把内容在指定边缘淡出。
 *
 * 胶囊条右缘渐隐与更新弹窗的说明滚动区共用这一份实现，避免两处各写一遍 Offscreen + 遮罩。
 */
internal fun Modifier.edgeFadeMask(brushFactory: DrawScope.() -> Brush): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent
        drawRect(brush = brushFactory(), blendMode = BlendMode.DstIn)
    }

/**
 * 竖向边缘羽化：滚动区上下两侧的内容淡出，替代硬裁切。
 *
 * 只在 [top] / [bottom] 为 true 的一侧铺渐变：滚到顶或底时对应一侧完全实心，
 * 不会出现"永远有雾"的假暗示；两侧都为 false 时不增加离屏图层。
 */
internal fun Modifier.verticalEdgeFade(
    top: Boolean,
    bottom: Boolean,
    fade: Dp = EdgeFadeWidth,
): Modifier {
    if (!top && !bottom) return this
    return edgeFadeMask {
        val fadePx = fade.toPx().coerceAtMost(size.height / 2f)
        val stop = (fadePx / size.height).coerceIn(0f, 0.5f)
        when {
            top && bottom -> Brush.verticalGradient(
                0f to Color.Transparent,
                stop to Color.Black,
                1f - stop to Color.Black,
                1f to Color.Transparent,
            )
            top -> Brush.verticalGradient(0f to Color.Transparent, stop to Color.Black)
            else -> Brush.verticalGradient(1f - stop to Color.Black, 1f to Color.Transparent)
        }
    }
}

/**
 * 封面骨架屏状态：图片首帧落地（成功或失败）后置位，微光随之停下。
 *
 * 只认成功会让加载失败的行一直闪——弱网下失败很常见，持续闪烁比原来的纯灰底更刺眼。
 */
@Stable
internal class CoverSkeletonState(settled: Boolean) {
    var settled by mutableStateOf(settled)
        private set

    /** 记录 Coil 的加载结果：成功与失败都算落地，只忽略仍在途中的状态。 */
    fun onImageState(state: AsyncImagePainter.State) {
        if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
            settled = true
        }
    }
}

/** 按封面 URL 记住骨架屏状态：换 URL 重新开闪，同一 URL 复用既有状态。 */
@Composable
internal fun rememberCoverSkeletonState(url: String?): CoverSkeletonState =
    remember(url) { CoverSkeletonState(settled = url.isNullOrBlank()) }

/** 封面骨架屏底衬：灰底 + 微光，配合 [CoverSkeletonState.onImageState] 在图片落地后自动停闪。 */
@Composable
internal fun Modifier.coverSkeleton(state: CoverSkeletonState): Modifier =
    this.background(MiuixTheme.colorScheme.surfaceContainerHigh)
        .shimmer(visible = !state.settled)
