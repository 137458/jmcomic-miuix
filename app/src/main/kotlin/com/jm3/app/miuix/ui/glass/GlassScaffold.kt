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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
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
import top.yukonga.miuix.kmp.blur.Backdrop
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
 * GlassScaffold 采样的内容图层。浮层（FAB 槽等）通过它拿到与顶栏同一份 backdrop，
 * 才能对内容做模糊与折射而不是采样到空白；脱离 GlassScaffold 使用时为 null，浮层退化为实色。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 页面底色采样源：列表内浮层（页签等）与正文同处内容层，直接采样 [LocalGlassBackdrop]
 * 会把这棵子树（含浮层自身）一起糊进胶囊，只能采样"背后的页面底色"。
 * 由 GlassScaffold 提供；脱离时为空，浮层退回实色。
 */
val LocalGlassPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * GlassScaffold 提供的顶栏折叠行为。[GlassTopAppBar] 自动取用；页面无需关心嵌套滚动的挂载。
 * 允许为 null：脱离 GlassScaffold 使用玻璃顶栏时退化为不折叠。
 */
val LocalGlassScrollBehavior = compositionLocalOf<ScrollBehavior?> { null }

/**
 * 顶栏展开态实测高度。[GlassPullToRefresh] 用它作为刷新指示器的恒定下移量：
 * 指示器只在列表顶端出现，此时顶栏必然是展开态，取展开高度就是正确值。
 * 若改为转发页面的净空，库内的 Modifier.offset 会在组合期读折叠位移，折叠与惯性收尾期间逐帧重组。
 * 默认 0.dp：脱离 GlassScaffold 使用时指示器从容器顶端起排。
 */
val LocalGlassTopBarExpandedHeight = compositionLocalOf { 0.dp }

/**
 * 顶栏渐变模糊的满额半径、过渡曲线与渐变区间：采用 Miuix 官方 BlurredBar 配方
 * （10dp / curve 2.2 / ProgressiveBlur.Top 的默认区间 0f..1f）。
 * 渐变零点必须正好落在模糊层绘制区域的下沿：若把 endFraction 推到 1 以外，模糊在节点边界
 * 仍有强度而节点突然结束，顶栏底缘就会出现一条可见的硬切分界线（旧实现的问题）。
 */
private const val TOP_BAR_BLUR_RADIUS = 10f
private const val TOP_BAR_BLUR_CURVE = 2.2f
private const val TOP_BAR_BLUR_START_FRACTION = 0f
private const val TOP_BAR_BLUR_END_FRACTION = 1f

/**
 * 模糊层的滚动淡入距离：内容未滚动时顶栏保持完全透明，滚动超过这段距离后才淡入满额模糊，
 * 与官方 BlurredBar 的滚动联动一致，回顶时不会再有一层静止的模糊压在内容上。
 */
private val TopBarBlurFadeInDistance = 48.dp

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
 * 4. 内容容器挂上折叠行为的嵌套滚动节点，页内任意滚动容器都能驱动顶栏折叠；
 * 5. 顶栏背后的渐变模糊是一个独立图层，随内容滚动淡入，渐变零点落在顶栏下沿，
 *    顶栏内的分类/筛选页签同样处在模糊覆盖范围内。
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
    val pageBackdrop = rememberPageBackdrop()
    val shaderReady = backdrop != null && isRuntimeShaderSupported()
    val containerColor = MiuixTheme.colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val density = LocalDensity.current
    val systemBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val initialTopBarHeightPx = with(density) { (systemBarTop + TopAppBarDefaults.CollapsedHeight).roundToPx() }
    // 展开态的最大高度：折叠过程中顶栏实测高度会收缩，这里只记录最大值，再由折叠位移把当前高度算回来；
    // 初始高度使用 状态栏 + 默认顶栏高度 兜底，避免首帧高度为 0 导致内容跳动与转场坐标测偏。
    var topBarMaxHeightPx by remember { mutableIntStateOf(initialTopBarHeightPx) }
    val topBarExpandedHeight = with(density) { topBarMaxHeightPx.toDp() }
    val floatingBarContentPadding = LocalFloatingBarContentPadding.current
    // 主框架已经消费过系统栏 inset，这里取"剩余 inset"与安全值中的较大者：
    // 子页面（未被消费）不会贴到手势条/三键导航上，主 tab 也不会重复让位。
    val systemBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalGlassPageBackdrop provides pageBackdrop,
    ) {
        // FAB 槽与内容层不在同一棵子树里，通过 CompositionLocal 传递采样源。
        Scaffold(
            modifier = modifier,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
        ) { innerPadding ->
            // bottom 不含折叠位移，是常量；折叠位移由 CollapsingContentPadding 留到测量期再读。
            val contentBottomPadding = maxOf(innerPadding.calculateBottomPadding(), systemBarBottom) +
                floatingBarContentPadding +
                BottomContentBreathingSpace
            // 固定实例身份：每次重组都新建 PaddingValues 会让调用侧拿到的参数每次都不相等，
            // 内容子树的测量缓存因此作废，本与折叠无关的重组（主题切换、页面状态刷新）也被放大成整页重新测量。
            val contentPadding = remember(
                topBarExpandedHeight,
                contentBottomPadding,
                scrollBehavior.state,
                density,
            ) {
                CollapsingContentPadding(
                    expandedTop = topBarExpandedHeight,
                    state = scrollBehavior.state,
                    density = density,
                    bottom = contentBottomPadding,
                )
            }
            // 折叠行为对内容同样可见：页面里的 GlassTopBarScrollSync 需要它补同步
            CompositionLocalProvider(
                LocalGlassScrollBehavior provides scrollBehavior,
                LocalGlassTopBarExpandedHeight provides topBarExpandedHeight,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdropSource(backdrop)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        content(contentPadding)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .then(
                                if (shaderReady) {
                                    Modifier
                                } else {
                                    // 不支持 RuntimeShader 时退化为实色，避免顶栏内容叠在正文上。
                                    Modifier.background(containerColor)
                                },
                            ),
                    ) {
                        if (shaderReady) {
                            // 模糊层单独成节点：滚动淡入的 alpha 只作用于模糊与底色，
                            // 不会把叠在其上的标题、页签一起淡出。
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .topBarProgressiveBlur(backdrop)
                                    .topBarBlurFadeIn(scrollBehavior.state),
                            )
                        }
                        // 实测高度只取顶栏内容本身：模糊层不计入内容净空、折叠位移与展开高度。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { topBarMaxHeightPx = maxOf(topBarMaxHeightPx, it.height) },
                        ) {
                            topBar()
                        }
                    }
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
 * 顶栏渐变模糊：标题区满额模糊，沿 Y 轴向下连续衰减，并在模糊层下沿精确归零，
 * 与正文之间是无缝过渡（既无硬切边，也无描边高光）。底色以低透明度 surface 混入保证标题可读。
 * 配方与官方 BlurredBar 一致：ProgressiveBlur.Top + curve 2.2 + 10dp 半径。
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
        gradient = ProgressiveBlur.Top.copy(
            startFraction = TOP_BAR_BLUR_START_FRACTION,
            endFraction = TOP_BAR_BLUR_END_FRACTION,
            curve = TOP_BAR_BLUR_CURVE,
        ),
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
 *
 * 实例由 [GlassScaffold] 的 remember 固定：折叠位移的状态读取只在测量期发生，重组不会换掉传下去的对象。
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
 * 顶栏占位：垂直滚动页面的内容起点要落在浮层顶栏下沿，高度取 [paddingValues] 当前的 top
 * （顶栏展开高度 + 折叠位移）。
 *
 * 组合期写 Spacer(Modifier.height(paddingValues.calculateTopPadding())) 会把折叠位移读在组合阶段：
 * 顶栏移动一帧就重组整页 Column。这里把读取放进 [Layout] 的测量阶段，
 * 位移变化只触发重新测量，占位高度仍随折叠平滑变化，页面内容不重组。
 */
@Composable
fun TopBarContentSpacer(paddingValues: PaddingValues) {
    Layout(content = {}) { _, constraints ->
        val topPx = paddingValues.calculateTopPadding().roundToPx()
        // 宽度沿用 Spacer 的策略：只有父级给到固定宽度才占满，否则不参与 Column 的宽度协商
        val width = if (constraints.hasFixedWidth) constraints.maxWidth else 0
        val height = if (constraints.hasBoundedHeight) {
            topPx.coerceIn(constraints.minHeight, constraints.maxHeight)
        } else {
            topPx
        }
        layout(width, height) {}
    }
}

/**
 * 顶栏模糊的滚动淡入：alpha 在 layer 块里读取折叠状态，滚动过程中只触发重绘、不触发重组。
 * contentOffset 为负值并随下滑累计，取相反数归一化后钳制到 0..1：回顶时完全透明，
 * 滚过 [TopBarBlurFadeInDistance] 后达到满额。
 */
private fun Modifier.topBarBlurFadeIn(state: TopAppBarState): Modifier = graphicsLayer {
    val distancePx = TopBarBlurFadeInDistance.toPx()
    alpha = (-state.contentOffset / distancePx).coerceIn(0f, 1f)
}
