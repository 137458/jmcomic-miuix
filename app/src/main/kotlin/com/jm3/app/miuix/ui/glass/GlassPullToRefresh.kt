// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshDefaults
import top.yukonga.miuix.kmp.basic.PullToRefreshState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

/**
 * 带顶栏折叠联动的下拉刷新：与 Miuix `PullToRefresh` 同签名，内部把 [LocalGlassScrollBehavior]
 * 交给刷新组件。
 *
 * 为什么必须由这里转发：刷新组件自身的嵌套滚动节点在内层，手指在列表顶端下拉时会先被它吃掉，
 * 外层 [GlassScaffold] 的折叠节点收不到下滑增量，顶栏因此永远停在折叠态（顶部留下常驻空缺）。
 * 直接把行为传下去后，刷新组件会按库内的责任链把它排在刷新手势之前。
 *
 * 转发的是 [GlassExpandScrollBehavior] 而不是原始行为：原行为的连接已经挂在 GlassScaffold 的内容
 * 容器上，同一个连接被两层同时调用会导致折叠/展开被计两次，这里只保留"顶端下拉展开"这一段。
 *
 * 传给库的 [contentPadding] 会把 top 换成常量 [LocalGlassTopBarExpandedHeight]（顶栏展开高度），
 * 而不是页面的实时净空：指示器只在列表顶端可见，那一刻顶栏必然是展开态，两者取值相同；
 * 换成常量后库内定位指示器的 offset() 不再在组合期读折叠位移。
 */
@Composable
fun GlassPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    color: Color = PullToRefreshDefaults.color,
    circleSize: Dp = PullToRefreshDefaults.circleSize,
    refreshTexts: List<String> = PullToRefreshDefaults.refreshTexts,
    refreshTextStyle: TextStyle = PullToRefreshDefaults.refreshTextStyle,
    onPullProgress: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val glassScrollBehavior = LocalGlassScrollBehavior.current
    val expandScrollBehavior = remember(glassScrollBehavior) {
        glassScrollBehavior?.let(::GlassExpandScrollBehavior)
    }
    // 刷新指示器只在列表顶端出现，此时顶栏必然处于展开态，因此它的下移量恒等于顶栏展开高度。
    // 若原样转发页面的净空，库内的 offset() 会在组合期读折叠位移，折叠与惯性收尾期间逐帧重组。
    val expandedTopPadding = LocalGlassTopBarExpandedHeight.current
    val headerOffsetPadding = remember(contentPadding, expandedTopPadding) {
        FixedTopPaddingValues(delegate = contentPadding, top = expandedTopPadding)
    }
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        pullToRefreshState = pullToRefreshState,
        contentPadding = headerOffsetPadding,
        topAppBarScrollBehavior = expandScrollBehavior,
        color = color,
        circleSize = circleSize,
        refreshTexts = refreshTexts,
        refreshTextStyle = refreshTextStyle,
        onPullProgress = onPullProgress,
        content = content,
    )
}

/**
 * 只替换 top 的净空：库内只用 top 定位刷新指示器；bottom/left/right 不含折叠位移，是常量，原样转发。
 */
private class FixedTopPaddingValues(
    private val delegate: PaddingValues,
    private val top: Dp,
) : PaddingValues {

    override fun calculateTopPadding(): Dp = top

    override fun calculateBottomPadding(): Dp = delegate.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        delegate.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        delegate.calculateRightPadding(layoutDirection)
}

/**
 * 只负责"列表到顶后下拉展开顶栏"的滚动行为。
 *
 * 上滑折叠由 GlassScaffold 内容容器上的原始连接处理（它先于刷新组件收到上滑增量）；
 * 惯性收尾也交给它，避免两个节点对同一 [TopAppBarState] 各起一个吸附动画。
 */
private class GlassExpandScrollBehavior(
    private val delegate: ScrollBehavior,
) : ScrollBehavior {

    override val state: TopAppBarState get() = delegate.state

    override val isPinned: Boolean get() = delegate.isPinned

    override val snapAnimationSpec: AnimationSpec<Float>? get() = delegate.snapAnimationSpec

    override val flingAnimationSpec: DecayAnimationSpec<Float>? get() = delegate.flingAnimationSpec

    override val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // 只有"内容已到顶、没有被消费掉的下滑"才展开顶栏。
            if (available.y <= 0f) return Offset.Zero
            return delegate.nestedScrollConnection.onPostScroll(consumed, available, source)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity.Zero
    }
}
