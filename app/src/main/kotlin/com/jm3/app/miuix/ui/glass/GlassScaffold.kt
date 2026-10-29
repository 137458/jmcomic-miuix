// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 悬浮底栏的内容净空（底栏高度 + 底边距 + 呼吸空间）。主框架展示底栏时提供 100.dp，
 * 无底栏的子页面与宽屏侧栏模式保持默认 0。GlassScaffold 把它叠加进 bottom 内边距，
 * 让列表末项能滚出胶囊上方，同时内容其余部分可穿透底栏滚动、持续被玻璃采样。
 */
val LocalFloatingBarContentPadding = compositionLocalOf { 0.dp }

/**
 * GlassScaffold 提供的顶栏折叠行为。[GlassTopAppBar] 自动取用；页面无需关心嵌套滚动的挂载。
 * 允许为 null：脱离 GlassScaffold 使用玻璃顶栏时退化为不折叠。
 */
val LocalGlassScrollBehavior = compositionLocalOf<ScrollBehavior?> { null }

/** 顶栏渐变模糊半径与过渡曲线：与 MIUIX 官方 BlurredBar 规范一致（HyperOS 沉浸式顶栏）。 */
private const val TOP_BAR_BLUR_RADIUS = 10f
private const val TOP_BAR_BLUR_CURVE = 2.2f

/** 折叠遮罩：完全折叠时的顶部不透明度、渐变终点不透明度与渐变截止位置。 */
private const val TOP_BAR_SCRIM_ALPHA = 0.9f
private const val TOP_BAR_SCRIM_FADE_ALPHA = 0.55f
private const val TOP_BAR_SCRIM_FADE_STOP = 0.7f

/**
 * 内容底部的隐式呼吸空间。系统导航条 inset 只保证"不被系统遮挡"，末项贴着屏幕底缘仍然拥挤，
 * 因此统一再留一段间距；页面不必各自补 Spacer，子页面与带底栏的主页面因此保持同一收尾节奏。
 */
private val BottomContentBreathingSpace = 20.dp

/**
 * 玻璃顶栏脚手架：顶栏改为覆盖层，内容穿透到状态栏下方滚动，顶栏实时采样其下方内容，
 * 并随内容滚动折叠成大标题 → 小标题。
 *
 * 与 Miuix `Scaffold` 的差异：
 * 1. 顶栏不再占位，而是作为浮层叠在内容之上；
 * 2. 传给 [content] 的 top 内边距随顶栏折叠实时收缩（等价于顶栏当前高度），调用方需把它作为
 *    滚动容器的 `contentPadding`（而非 `Modifier.padding`），否则内容退到顶栏下方就无法被采样；
 * 3. bottom 内边距额外叠加 [BottomContentBreathingSpace]，末项不会贴到导航条上；
 * 4. 内容容器挂上折叠行为的嵌套滚动节点，页内任意滚动容器都能驱动顶栏折叠。
 *
 * 非滚动内容（表单、居中空态/错误态）继续用 `Modifier.padding(innerPadding)` 即可，
 * 顶栏此时按实色兜底渲染。
 */
@Composable
fun GlassScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberBlurBackdrop()
    val shaderReady = backdrop != null && isRuntimeShaderSupported()
    val containerColor = MiuixTheme.colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    // 展开态的最大高度：折叠过程中顶栏实测高度会收缩，这里只记录最大值，再由折叠位移把当前高度算回来
    var topBarMaxHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topBarExpandedHeight = with(density) { topBarMaxHeightPx.toDp() }
    val floatingBarContentPadding = LocalFloatingBarContentPadding.current
    // 主框架已经消费过系统栏 inset，这里取"剩余 inset"与安全值中的较大者：
    // 子页面（未被消费）不会贴到手势条/三键导航上，主 tab 也不会重复让位。
    val systemBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        // 折叠行为对内容同样可见：页面里的 GlassTopBarScrollSync 需要它补同步
        CompositionLocalProvider(LocalGlassScrollBehavior provides scrollBehavior) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdropSource(backdrop)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    content(
                        CollapsingContentPadding(
                            expandedTop = topBarExpandedHeight,
                            state = scrollBehavior.state,
                            density = density,
                            bottom = maxOf(innerPadding.calculateBottomPadding(), systemBarBottom) +
                                floatingBarContentPadding +
                                BottomContentBreathingSpace,
                        ),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topBarMaxHeightPx = maxOf(topBarMaxHeightPx, it.height) }
                        .then(
                            if (shaderReady) {
                                Modifier.topBarProgressiveBlur(backdrop)
                            } else {
                                Modifier.background(containerColor)
                            },
                        ),
                ) {
                    // 折叠进度越高，同色遮罩越实：渐变模糊在底缘衰减到 0，只靠模糊时小标题
                    // 下方的正文仍然清晰可读，观感杂乱；补一层渐变底色把标题区压实。
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .topBarScrim(state = scrollBehavior.state, color = containerColor),
                    )
                    topBar()
                }
            }
        }
    }
}

/**
 * 玻璃顶栏：把 Miuix `TopAppBar` 接到 [GlassScaffold] 的折叠行为上。
 *
 * 默认背景透明，由 GlassScaffold 的渐变模糊层负责底色，因此页面里只需替换组件名，
 * 不必再写 `color = Color.Transparent`，也不必自己挂 `nestedScroll`。
 */
@Composable
fun GlassTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        color = color,
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = largeTitleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = LocalGlassScrollBehavior.current,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        bottomContent = bottomContent,
    )
}

/**
 * 不折叠的玻璃顶栏：用于阅读等沉浸页，标题单行展示，把纵向空间让给内容。
 *
 * 转调 Miuix [SmallTopAppBar]；同一 ScrollBehavior 会因此被钉在展开态，滚动时高度恒定。
 */
@Composable
fun GlassSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    SmallTopAppBar(
        title = title,
        modifier = modifier,
        color = color,
        titleColor = titleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = LocalGlassScrollBehavior.current,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        bottomContent = bottomContent,
    )
}

/**
 * 顶栏渐变模糊：状态栏区域满额模糊，沿 Y 轴向下平滑衰减至底缘 0（无硬切边、无描边高光），
 * 底色以低透明度 surface 混入保证标题可读。与 MIUIX 官方 BlurredBar 同一配方。
 */
@Composable
private fun Modifier.topBarProgressiveBlur(backdrop: LayerBackdrop): Modifier {
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
        ),
    )
    return progressiveTextureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        gradient = ProgressiveBlur.Top.copy(curve = TOP_BAR_BLUR_CURVE),
        blurRadius = TOP_BAR_BLUR_RADIUS,
        colors = blurColors,
    )
}

/**
 * 内容净空：top 跟着顶栏折叠实时收缩，bottom 为固定净空。
 *
 * 折叠过程中的取值全部发生在布局阶段（滚动容器的 `contentPadding`、`Modifier.padding` 都在测量期
 * 读取），因此顶栏收缩只触发重新测量，不触发重组。
 *
 * top = 展开态高度 + 折叠位移（`TopAppBarState.heightOffset` 为 0 ~ -展开增量的负值）：
 * 折叠时内容跟着上移，不会在顶栏与首项之间留下静态空缺；回顶展开时内容同步下移。
 */
private class CollapsingContentPadding(
    private val expandedTop: Dp,
    private val state: TopAppBarState,
    private val density: Density,
    private val bottom: Dp,
) : PaddingValues {

    override fun calculateTopPadding(): Dp =
        (expandedTop + with(density) { state.heightOffset.toDp() }).coerceAtLeast(0.dp)

    override fun calculateBottomPadding(): Dp = bottom

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

/**
 * 在 [GlassScaffold] 的内容净空上叠加水平内缩与额外的上下间距（列表自身的内缩、与筛选条的间隙等）。
 *
 * 直接 `PaddingValues(top = innerPadding.calculateTopPadding() + 8.dp)` 会在组合阶段读折叠位移，
 * 折叠过程中逐帧重组；用本函数包一层，读取推迟到布局阶段，顶栏收缩只触发重新测量。
 * [bottom] 非空时替换原 bottom（个别页面只保留自身的内缩）。
 */
fun PaddingValues.withSpacing(
    horizontal: Dp = 0.dp,
    extraTop: Dp = 0.dp,
    bottom: Dp? = null,
): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding(): Dp = this@withSpacing.calculateTopPadding() + extraTop

    override fun calculateBottomPadding(): Dp = bottom ?: this@withSpacing.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        this@withSpacing.calculateLeftPadding(layoutDirection) + horizontal

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        this@withSpacing.calculateRightPadding(layoutDirection) + horizontal
}

/**
 * 顶栏折叠遮罩：按折叠进度叠加同色渐变，把标题区从背景内容里分离出来。
 * 颜色读取放在绘制阶段，滚动过程中只触发重绘，不触发重组。
 */
private fun Modifier.topBarScrim(state: TopAppBarState, color: Color): Modifier = drawBehind {
    val fraction = state.collapsedFraction.coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawBehind
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = TOP_BAR_SCRIM_ALPHA * fraction),
            TOP_BAR_SCRIM_FADE_STOP to color.copy(alpha = TOP_BAR_SCRIM_FADE_ALPHA * fraction),
            1f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        ),
        size = size,
    )
}
