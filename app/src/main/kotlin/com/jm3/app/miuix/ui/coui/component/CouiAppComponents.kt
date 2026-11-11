// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarColors
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.jm3.app.miuix.ui.MiuixDockedNavItem
import com.jm3.app.miuix.ui.MiuixDockedNavigationBar
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.bottomBarShape
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentSegment
import com.jm3.core.paging.PagedFeed
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults

/**
 * 由 [CouiAppScaffold] 提供的正文区采样源：仅当页面装配了带 [CouiBackdrop] 需求的顶栏扩展条
 * 或浮层时创建（API 34+），让库顶栏的同级扩展控件（`ChipTabRow` / `SegmentedControl`）可采样滚动正文。
 */
val LocalCouiAppBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

/**
 * COUI 页面骨架：顶栏、底栏净空与 FAB 都在本页 Scaffold 装配。
 *
 * 底栏由主框架单实例绘制在页面之上，转场期不会随两个页面各组合一份；
 * 这里只按它公布的净空值在 bottomBar 槽位留出等高占位，
 * 让正文的 innerPadding 与 FAB 位置一起抬到底栏之上。
 */
@Composable
fun CouiAppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable BoxScope.() -> Unit)? = null,
    contentBackdrop: CouiBackdrop? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 净空值不含系统导航栏 inset，占位也要让出同一条底部安全区
    val barClearance = LocalFloatingBarContentPadding.current
    // 库 Scaffold 的 windowInsets 只含系统栏，IME 避让按库要求由宿主缩小视口（Scaffold.kt:45）
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            CompositionLocalProvider(LocalCouiAppBackdrop provides contentBackdrop) {
                topBar()
            }
        },
        bottomBar = {
            if (barClearance > 0.dp) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(barClearance)
                        .navigationBarsPadding(),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        content = { innerPadding ->
            if (contentBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .couiBackdrop(contentBackdrop)
                        .background(CouiTheme.colorScheme.background),
                ) {
                    content(innerPadding)
                }
            } else {
                content(innerPadding)
            }
        },
    )
}

/** 可点件最小触摸目标：无障碍基线 48dp，低于它的可见按钮靠留白补足触区。 */
val CouiMinTouchTarget = 48.dp

/**
 * 破坏性确认按钮：红底白字的主操作。
 *
 * 库把破坏性文字色只留给 neutral 槽，而 neutral 属于第三个按钮；两按钮弹窗里
 * confirmAction 是必填项，只能用 Primary 样式并显式注入主题的破坏性配色，
 * 按钮顺序仍是取消在左、确认在右。
 */
@Composable
fun couiDestructiveConfirmAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
): CouiDialogAction {
    val destructive = CouiDialogDefaults.themeColors(CouiTheme.isDark).destructive
    return CouiDialogAction(
        text = text,
        enabled = enabled,
        style = CouiDialogActionStyle.Primary,
        colors = CouiDialogDefaults.primaryActionColors(destructive),
        onClick = onClick,
    )
}

/**
 * 按压反馈 + 无涟漪点击：COUI 页面里所有自绘可点区域的统一入口。
 *
 * 反馈分三层：库的 [couiButtonFeedback] 给 hover/focus/press 蒙层，[couiPressMotion] 给原厂
 * 按压缩放（按面积与宽高比取 0.85–0.97，宽条走糖果曲线），按下瞬间再由
 * [performCouiHapticFeedback] 发一次 COUI 触觉。列表行只叠蒙层，
 * 整行随按下缩放会压过行内的滑选与长按，所以缩放由 [scaleOnPress] 显式打开。
 * 点击指示器置空，避免在实底卡片上再叠一层涟漪。语义角色由调用方决定，默认按钮。
 *
 * @param minTouchTarget 补足触区的最小边长；视觉内容仍按原尺寸居中，只有可点范围变大。
 * 本修饰符必须写在固定尺寸之前（`Modifier.couiPressable(...).size(40.dp)`）：写在 `.size()` 之后时，
 * 外层固定尺寸把子约束钉死，扩边会被 coerceAtMost 钳回原尺寸，触区等于视觉尺寸。
 * 胶囊或圆标的底色须通过 [backgroundColor] 传入（不可在调用点外侧先 `.clip().background()`），
 * 底色、裁剪、按压蒙层与凝光才会一起留在视觉尺寸内，同时点击命中仍覆盖完整 [minTouchTarget] 区域。
 * @param scaleOnPress 小型控件（标签、图标键、胶囊）打开原厂缩放；整行列表项保持关闭。
 * @param spotlight 凝光材质预设；传 null 关闭。默认取 [SpotlightPreset.OpaqueSmall2]：
 * 与库顶栏按钮同档的中性表面，落在不透明底上按 OVERLAY 叠光。
 * @param spotlightAboveContent 内容是不透明图像（封面、缩略图）时打开：光改画在内容之上，
 * 否则会被图像整个盖住；文字与图标为主的表面保持关闭，光落在底色与内容之间。
 */
@Composable
fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape? = null,
    backgroundColor: Color = Color.Unspecified,
    minTouchTarget: Dp = 0.dp,
    scaleOnPress: Boolean = false,
    spotlight: SpotlightPreset? = SpotlightPreset.OpaqueSmall2,
    spotlightAboveContent: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    // 每次按住只发一次：滑动、长按都不会再触发，避免连续震动
    LaunchedEffect(interactionSource, view) {
        var down = false
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                if (!down) {
                    down = true
                    view.performCouiHapticFeedback(CouiHapticFeedback.Switch)
                }
            } else if (interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) {
                down = false
            }
        }
    }
    val press = if (scaleOnPress) rememberCouiPressMotion() else null
    // 视觉表面全部留在 couiMinimumTouchTarget 内侧（后置）：底色只铺在视觉胶囊/圆标内，
    // 凝光落在底色之后、内容之前；放大比例只在视觉层施加，手势与点击检测放在触区外侧。
    val visualSurface = Modifier
        .then(
            if (press != null) {
                Modifier.graphicsLayer {
                    scaleX = press.scale.value
                    scaleY = press.scale.value
                }
            } else {
                Modifier
            },
        )
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .then(if (backgroundColor != Color.Unspecified) Modifier.background(backgroundColor) else Modifier)
        .couiButtonFeedback(
            dark = CouiTheme.isDark,
            interactionSource = interactionSource,
            enabled = enabled,
            list = false,
        )
        .couiSpotlightLight(
            preset = spotlight,
            interactive = enabled,
            aboveContent = spotlightAboveContent,
        )
    val pressDetector = if (press != null) {
        Modifier.couiPressMotion(press = press, enabled = enabled, cancelOnConsumedMove = true)
    } else {
        Modifier
    }

    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        .then(pressDetector)
        .couiMinimumTouchTarget(minTouchTarget)
        .then(visualSurface)
}

/**
 * 凝光叠层：把库 Spotlight 的输入观察与绘制接到同一个节点上。
 *
 * 输入侧只在 Initial 阶段读指针位置、不消费事件；绘制侧默认在内容之前叠光，
 * 因此本修饰符必须紧跟在「不透明底色 + clip」之后、内容之前，且不可跨节点复用。
 * [color] 是凝光的取色，库的浮动节点传内容色（NavigationBar.kt:433），不传则用预设自带的白光。
 * [aboveContent] 把光改画在子内容之上：卡片这类表面由不透明画面（封面图）铺满，
 * 光画在内容之下会被整个盖住，只剩页边一条可见；库的弹窗按钮同样把光画在内容之上
 * （CouiDialogButton.kt:82-84 drawWithContent 里先 drawContent 再 drawLight）。
 * API < 33 时输入与绘制都是空实现（CouiSpotlight.kt:100-115），调用点不必写版本分支。
 */
@Composable
internal fun Modifier.couiSpotlightLight(
    preset: SpotlightPreset?,
    interactive: Boolean,
    color: Color? = null,
    aboveContent: Boolean = false,
): Modifier {
    if (preset == null) return this
    val (input, draw) = rememberCouiSpotlight(
        interactive = interactive,
        preset = preset,
        dark = CouiTheme.isDark,
        // 库的浮动节点把内容色一并交给凝光（NavigationBar.kt:433），这里保持同一口径
        color = color,
    )
    val layer = if (aboveContent) {
        Modifier.drawWithContent {
            drawContent()
            draw?.invoke(this)
        }
    } else {
        Modifier.drawBehind { draw?.invoke(this) }
    }
    return this.then(input).then(layer)
}

/**
 * 把可点区域补到 [target]：内层按原尺寸测量，外层报补足后的边长并居中放置。
 *
 * 触摸命中按布局节点的最终尺寸判定，因此触区随外层一起变大，可见内容不缩放。
 */
/**
 * 顶栏标题槽的标准写法。
 *
 * COUI 顶栏会把标题字号、字重与颜色下发到 [LocalCouiTextStyle]，折叠时再按
 * collapsedTitleSize/expandedTitleSize 的比值整体缩放这个样式。槽内若用 Compose 默认字号的
 * BasicText，作者拿不到这套字阶（详情页曾因此落回 16sp），所以统一走本函数。
 */
@Composable
fun CouiTopBarTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val style = LocalCouiTextStyle.current
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style else style.copy(textAlign = textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 顶栏底色取皮肤页面底色。
 *
 * 库默认背景是纯黑/纯白，深色下还会叠一层 40% 黑渐变（TopAppBarSurface），在自带绿调的
 * 页底上就是一条黑带；这里改用皮肤 background，让顶栏与正文同底。
 */
@Composable
fun couiTopAppBarColors(): CouiTopAppBarColors = CouiTopAppBarDefaults.colors(
    backgroundColor = CouiTheme.colorSchemeFor(CouiTheme.isDark).background,
    dark = CouiTheme.isDark,
)

private fun Modifier.couiMinimumTouchTarget(target: Dp): Modifier {
    if (target <= 0.dp) return this
    return layout { measurable, constraints ->
        val targetPx = target.roundToPx()
        val placeable = measurable.measure(constraints)
        val width = placeable.width
            .coerceAtLeast(targetPx)
            .coerceAtMost(constraints.maxWidth)
            .coerceAtLeast(constraints.minWidth)
        val height = placeable.height
            .coerceAtLeast(targetPx)
            .coerceAtMost(constraints.maxHeight)
            .coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}

/** 加载态：单行说明卡片；首屏与分页都能直接放，尺寸由库 Group/Item 的 CouiPageDefaults 决定。 */
@Composable
fun CouiLoadingGroup(
    modifier: Modifier = Modifier,
    text: String = "正在加载…",
) {
    Group(modifier = modifier) {
        Item(
            title = {
                CouiBodyText(text = text, textAlign = TextAlign.Start)
            },
        )
    }
}

/** 空态：单行说明卡片；文案由页面给出（例如「这里还没有作品」）。 */
@Composable
fun CouiEmptyGroup(
    text: String,
    modifier: Modifier = Modifier,
) {
    Group(modifier = modifier) {
        Item(
            title = {
                CouiBodyText(text = text, textAlign = TextAlign.Start)
            },
        )
    }
}

/** 失败态：标题给结论、脚注给原因，「重试」行始终可见且可点。 */
@Composable
fun CouiErrorGroup(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    Group(modifier = modifier, title = CouiStrings.LoadFailed, footer = message) {
        Item(
            title = {
                CouiBodyText(text = CouiStrings.Retry, textAlign = TextAlign.Start)
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Refresh,
                    // 行标题已念出「重试」，图标不再重复播报
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
            },
            onClick = onRetry,
        )
    }
}

/**
 * 分页脚：滚动到底时报告续页状态。
 *
 * error 非空时给可点的「重新加载」行；loading 与 endReached 只报进度与到底；
 * 三者皆否表示还有下一页但尚未触发加载，此时只留一条提示文案，不做可点行。
 */
@Composable
fun CouiLoadMoreFooter(
    loading: Boolean,
    error: String?,
    endReached: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    when {
        error != null -> Group(modifier = modifier, footer = error) {
            Item(
                title = {
                    CouiBodyText(text = CouiStrings.Reload, textAlign = TextAlign.Start)
                },
                trailing = {
                    Icon(
                        imageVector = CouiIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.accent,
                    )
                },
                onClick = onRetry,
            )
        }

        loading -> Group(modifier = modifier, footer = "正在获取下一页…") {
            Item(
                title = {
                    CouiBodyText(text = "正在加载更多…", textAlign = TextAlign.Start)
                },
            )
        }

        endReached -> Group(modifier = modifier, footer = "没有更多了") {
            Item(
                title = {
                    CouiBodyText(text = "已经到底了", textAlign = TextAlign.Start)
                },
            )
        }

        else -> Footer(modifier = modifier, text = "继续上滑自动加载下一页")
    }
}

/**
 * 分页脚：直接吃整份 [PagedFeed]，调用方只交状态机与失败文案。
 *
 * 四分支由状态机推出，页面不再各自把 loadingMore/loadMoreFailed/hasMore 翻成三个布尔。
 */
@Composable
fun CouiLoadMoreFooter(
    feed: PagedFeed<*>,
    errorMessage: String = CouiStrings.LoadMoreFailedRetry,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    CouiLoadMoreFooter(
        loading = feed.loadingMore,
        error = if (feed.loadMoreFailed) errorMessage else null,
        endReached = !feed.hasMore,
        modifier = modifier,
        onRetry = onRetry,
    )
}

/** 库浮动材质导航栏需要 API 34：库内 CouiFloatingRenderer 依赖 34 起的模糊与渲染能力。 */
internal const val CouiLibraryNavBarMinSdk = 34

/** 悬浮档顶边留白：给投影与凝光外扩留出空间，与库 NavigationBar 的 16dp 一致。 */
private val CouiBarTopMargin = 16.dp

/** 悬浮档条身与系统触控区之间的底距，与库 NavigationBar 的 16dp 一致。 */
private val CouiBarBottomMargin = 16.dp

/** 设备没有手势条/导航栏触控区时，库会再加 8dp 底距；自绘分支跟随同一数值。 */
private val CouiBarGestureGap = 8.dp

/** 菜单项宽度下限与间隔，用于复算库的 maximumMenuWidth：24 + 70*n + 8*(n-1)。 */
private val CouiNavItemWidth = 70.dp
private val CouiNavItemSpacing = 8.dp
private val CouiNavSideSurfaceWidth = 136.dp

/** 底栏选中/未选中文字色透明度，与库 NavigationBar 的 230/255 与 184/255 一致。 */
private const val CouiNavSelectedAlpha = 230f / 255f
private const val CouiNavUnselectedAlpha = 184f / 255f

/** 底栏图标与标签之间的间距，与库 NavigationBar 的 2dp 一致。 */
private val CouiNavLabelGap = 2.dp

/** 悬浮档投影半径，与液态玻璃底栏同一族。 */
private val CouiBarShadowRadius = 10.dp

/** 选中容器透明度：底栏、侧栏与分段胶囊共用，保证所有选中观感一致。 */
internal const val CouiSelectedContainerAlpha = 0.14f

/** 侧栏宽度：只放图标列，横向固定不参与伸缩。 */
private val CouiRailWidth = 88.dp

/** 侧栏项与栏边之间的横向留白。 */
private val CouiRailItemMargin = 8.dp

/** 侧栏项内上下留白。 */
private val CouiRailItemVerticalPadding = 10.dp

/** 侧栏图标与文字之间的间距。 */
private val CouiRailLabelSpacing = 4.dp

/**
 * 是否交给库的浮动材质导航栏渲染。
 *
 * 只有「普通档 + 悬浮形态 + API 34 及以上」这一种组合保持库渲染：偏大档要靠 token 放大条高、
 * 图标与字号，贴底档要通栏与上圆角，低版本没有原厂浮动材质，三者都只能按 token 自绘。
 * sdkInt 由调用方传入，纯函数在 JVM 单测里可覆盖全部组合。
 */
internal enum class CouiNavBarMode {
    LIBRARY_FLOATING,
    LEGACY_FLOATING,
    MIUIX_DOCKED,
}

internal fun couiNavBarMode(tokens: GlassBottomBarTokens, sdkInt: Int): CouiNavBarMode = when {
    tokens.form == BottomBarForm.DOCKED -> CouiNavBarMode.MIUIX_DOCKED
    sdkInt >= CouiLibraryNavBarMinSdk && tokens.size == BottomBarSize.STANDARD -> CouiNavBarMode.LIBRARY_FLOATING
    else -> CouiNavBarMode.LEGACY_FLOATING
}

internal fun couiUsesLibraryNavBar(tokens: GlassBottomBarTokens, sdkInt: Int): Boolean =
    couiNavBarMode(tokens, sdkInt) == CouiNavBarMode.LIBRARY_FLOATING

/** 自绘底栏的宽度上限：复算库的 maximumMenuWidth + 两侧圆键留白，换实现时条宽不跳变。 */
internal fun couiPlainNavMaxWidth(itemCount: Int): Dp =
    24.dp + CouiNavItemWidth * itemCount +
        CouiNavItemSpacing * (itemCount - 1).coerceAtLeast(0) + CouiNavSideSurfaceWidth

/**
 * Compose 合成的取消手势没有平台事件，只会把上一帧原地转成已消费的抬手；
 * 与库内部的同名判定保持同一条件，避免把取消当成抬手派发导航。
 */
private fun PointerEvent.isCouiTouchCancelled(): Boolean =
    motionEvent == null && changes.any { it.previousPressed } && changes.all { change ->
        !change.pressed && (!change.previousPressed ||
            (change.isConsumed && change.uptimeMillis == change.previousUptimeMillis &&
                change.position == change.previousPosition))
    }

/**
 * 拖动预选的释放判定，语义与库 NavigationBar 的 UP 分发一致：
 * 释放点既不是按下项也不是预览项时切到释放点；否则预览项与待定项一致即切过去；
 * 按下即抬起的普通点击再补一次同点判定。被取消的手势不产生切换。
 */
internal fun couiPlainNavReleaseTarget(
    preview: Int,
    pending: Int?,
    down: Int?,
    up: Int?,
    cancelled: Boolean,
): Int? {
    if (!cancelled && up != null && up != down && up != preview) return up
    if (pending != null && pending == preview) return pending
    if (!cancelled && up != null && up == down && up != preview) return up
    return null
}

/**
 * COUI 底栏路由：
 * - [BottomBarForm.DOCKED]：双端共用 Miuix 官方 NavigationBar，不再手写贴底横条；
 * - [BottomBarForm.FLOATING] + API 34+：走 COUI 原厂浮动材质底栏；
 * - [BottomBarForm.FLOATING] + API 31–33：走 [CouiPlainNavigationBar] 标准浮动胶囊降级方案。
 */
@Composable
internal fun CouiNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tokens: GlassBottomBarTokens,
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
) {
    when (couiNavBarMode(tokens, Build.VERSION.SDK_INT)) {
        CouiNavBarMode.LIBRARY_FLOATING -> {
            // 先判版本再进库分支：@RequiresApi 守卫必须是 SDK_INT 比较，否则 Lint 报 CallRequiresApi
            if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
                CouiLibraryNavigationBar(
                    items = items,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    backdrop = backdrop,
                    modifier = modifier,
                )
            }
        }

        CouiNavBarMode.MIUIX_DOCKED -> {
            val scheme = CouiTheme.colorScheme
            MiuixDockedNavigationBar(
                items = remember(items) {
                    items.map { item -> MiuixDockedNavItem(label = item.title, icon = item.icon) }
                },
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                color = scheme.surface,
                itemColors = NavigationBarDefaults.navigationBarItemColors(
                    unselectedContentColor = scheme.onSurfaceVariant,
                    selectedContentColor = scheme.accentOnSurface,
                ),
                modifier = modifier,
            )
        }

        CouiNavBarMode.LEGACY_FLOATING -> {
            CouiPlainNavigationBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                tokens = tokens,
                modifier = modifier,
            )
        }
    }
}

/** 库浮动材质底栏：三个浮动节点（左右圆键 + 中间胶囊）共用主框架的 backdrop 采样。 */
@RequiresApi(34)
@Composable
private fun CouiLibraryNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { CouiFloatingRenderer() }
    NavigationBar(
        modifier = modifier,
        backdrop = backdrop,
        renderer = renderer,
        items = items,
        selectedIndex = selectedIndex,
        dark = CouiTheme.isDark,
        onSelection = onSelect,
    )
}

/**
 * 自研底栏：条高、图标、字号、横向边距与内边距全部读 token。
 *
 * 悬浮形态是胶囊加投影，贴底形态是通栏加上圆角、无投影；
 * 采用硬件级圆角裁剪并消除默认 Ripple，彻底杜绝按压黑块阴影。
 */
@Composable
private fun CouiPlainNavigationBar(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tokens: GlassBottomBarTokens,
    modifier: Modifier = Modifier,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val dark = CouiTheme.isDark
    val shape = bottomBarShape(tokens)
    val tabShape = RoundedCornerShape(tokens.indicatorHeight / 2)
    // 与库一致：选中只改颜色不改底，深色取白、浅色取黑，两档透明度固定
    val selectedColor = (if (dark) Color.White else Color.Black).copy(alpha = CouiNavSelectedAlpha)
    val unselectedColor = (if (dark) Color.White else Color.Black).copy(alpha = CouiNavUnselectedAlpha)
    // 按住横向滑动时先点亮途经菜单项、抬手再切换：预览值与选中值分开，离开节点回落选中值
    var previewSelection by remember { mutableIntStateOf(selectedIndex) }
    val currentSelection by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(selectedIndex) { previewSelection = selectedIndex }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    fun itemAt(x: Float): Int? {
        if (barWidthPx <= 0f || items.isEmpty()) return null
        return (x / barWidthPx * items.size).toInt().takeIf { it in items.indices }
    }

    val density = LocalDensity.current
    // 与库一致：有可点触控区（三键导航）时底距只留 16dp，手势条时再加 8dp；横向/底部 inset 交给系统
    val bottomMargin = if (WindowInsets.tappableElement.getBottom(density) > 0) 0.dp else CouiBarGestureGap

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(
                start = tokens.horizontalMargin,
                end = tokens.horizontalMargin,
                top = CouiBarTopMargin,
                bottom = CouiBarBottomMargin + bottomMargin,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = couiPlainNavMaxWidth(items.size))
                .fillMaxWidth()
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = CouiBarShadowRadius,
                        color = Color.Black,
                        alpha = if (CouiTheme.isDark) tokens.shadowAlphaDark else tokens.shadowAlphaLight,
                    ),
                )
                .clip(shape)
                .background(colors.container)
                .height(tokens.height)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .selectableGroup()
                // 手势由容器统一接管：按住拖动先点亮途经项，抬手按释放点切换，判定与库同一套
                .pointerInput(items.size) {
                    var active: PointerId? = null
                    var pending: Int? = null
                    var downItem: Int? = null
                    fun finish(cancelled: Boolean, position: Offset?) {
                        val target = couiPlainNavReleaseTarget(
                            preview = previewSelection,
                            pending = pending,
                            down = downItem,
                            up = position?.let { itemAt(it.x) },
                            cancelled = cancelled,
                        )
                        if (target != null) {
                            previewSelection = target
                            currentOnSelect(target)
                        }
                        active = null
                        pending = null
                        downItem = null
                    }
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                // 系统的取消手势没有平台事件、只会变成原地已消费的抬手，
                                // 认不出来就会被当成抬手派发一次导航
                                if (event.isCouiTouchCancelled()) {
                                    finish(cancelled = true, position = null)
                                    continue
                                }
                                val tracked = event.changes.firstOrNull { it.id == active }
                                val pointer = tracked?.takeIf { it.pressed }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: tracked ?: continue
                                if (active == null) {
                                    downItem = itemAt(pointer.position.x)
                                } else if (pointer.pressed && pointer.position != pointer.previousPosition) {
                                    itemAt(pointer.position.x)?.let { index ->
                                        if (previewSelection != index) {
                                            previewSelection = index
                                            pending = index
                                        }
                                    }
                                }
                                active = pointer.id
                                if (!pointer.pressed) finish(cancelled = false, position = pointer.position)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } finally {
                        // 节点移除不代表原厂 ACTION_CANCEL：离开页面只回落预览，不触发导航
                        previewSelection = currentSelection
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = previewSelection == index
                val press = rememberCouiPressMotion()
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(tokens.indicatorHeight)
                        .padding(horizontal = tokens.innerPadding)
                        .graphicsLayer {
                            scaleX = press.scale.value
                            scaleY = press.scale.value
                        }
                        .clip(tabShape)
                        // 蒙层在下、凝光在上：hover/press 蒙层先叠，凝光再按本项轮廓折射，内容最后画
                        .couiNavigationFeedback(dark = dark, interactionSource = interactionSource)
                        // 取色与图标文字同源，和库浮动节点传内容色的口径一致
                        .couiSpotlightLight(
                            preset = SpotlightPreset.ChipTab,
                            interactive = true,
                            color = if (selected) selectedColor else unselectedColor,
                        )
                        .couiPressMotion(press = press, cancelOnConsumedMove = false)
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            // 与本地同名 val 区分：这里是 SemanticsPropertyReceiver 的扩展属性
                            this.selected = selected
                            onClick {
                                if (index != currentSelection) currentOnSelect(index)
                                true
                            }
                        }
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                                if (index != currentSelection) currentOnSelect(index)
                                true
                            } else {
                                false
                            }
                        }
                        .focusable(interactionSource = interactionSource),
                    contentAlignment = Alignment.Center,
                ) {
                    // 原厂底栏图标与标签常显：竖排图标 + 10sp 标签，选中只换颜色
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.iconSize),
                            tint = if (selected) selectedColor else unselectedColor,
                        )
                        Spacer(Modifier.height(CouiNavLabelGap))
                        BasicText(
                            text = item.title,
                            style = TextStyle(
                                fontSize = tokens.labelSize,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = { if (selected) selectedColor else unselectedColor },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 宽屏侧栏：88dp 贴左固定宽度，图标与文字纵向等距排列，选中项取强调色容器。
 *
 * 顶部让出状态栏、底部让出导航栏，正文由 Row 的剩余空间接管；
 * 显式传入 indication = null 与连续曲率裁剪，杜绝按压方块阴影。
 */
@Composable
internal fun CouiNavigationRail(
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tokens: GlassBottomBarTokens,
    modifier: Modifier = Modifier,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    Column(
        modifier = modifier
            .width(CouiRailWidth)
            .fillMaxHeight()
            .background(colors.container)
            .statusBarsPadding()
            .navigationBarsPadding()
            .selectableGroup(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val press = rememberCouiPressMotion()
            val interactionSource = remember { MutableInteractionSource() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiRailItemMargin)
                    .graphicsLayer {
                        scaleX = press.scale.value
                        scaleY = press.scale.value
                    }
                    .clip(CouiCapsuleShape)
                    // 侧栏项自身没有不透明底：未选中时先垫容器色再叠凝光，避免光和页面底色相乘
                    .background(
                        if (selected) colors.accent.copy(alpha = CouiSelectedContainerAlpha) else colors.container,
                    )
                    .couiNavigationFeedback(dark = CouiTheme.isDark, interactionSource = interactionSource)
                    .couiSpotlightLight(
                        preset = SpotlightPreset.ChipTab,
                        interactive = true,
                        color = if (selected) colors.accent else colors.onSurfaceVariant,
                    )
                    .couiPressMotion(press = press, cancelOnConsumedMove = false)
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = CouiRailItemVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.icon,
                    // 文字标签常显，图标不再重复播报
                    contentDescription = null,
                    modifier = Modifier.size(tokens.iconSize),
                    tint = if (selected) colors.accent else colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(CouiRailLabelSpacing))
                BasicText(
                    text = item.title,
                    style = TextStyle(
                        fontSize = tokens.labelSize,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = { if (selected) colors.accent else colors.onSurfaceVariant },
                    maxLines = 1,
                )
            }
        }
    }
}

/** COUI FAB：API 34 及以上使用原厂浮动材质，低版本退回实底圆形按钮。 */
@Composable
fun CouiFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
        val renderer = remember { CouiFloatingRenderer.standalone() }
        // 原厂 Fab 自己下发内容色，但页面统一读 LocalCouiFallbackContentColor；
        // 这里把原厂内容色补进该 local，两种 API 分支取到同一个颜色。
        Fab(modifier = modifier, renderer = renderer, onClick = onClick) {
            CompositionLocalProvider(
                LocalCouiFallbackContentColor provides LocalCouiContentColor.current.takeOrElse {
                    CouiTheme.colorSchemeFor(CouiTheme.isDark).onAccent
                },
            ) {
                content()
            }
        }
    } else {
        val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
        Box(
            modifier = modifier
                .size(56.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                // 低版本没有原厂浮动材质：同一套按压缩放 + 触觉，凝光用原厂 Fab 的 OpaqueSmall1
                .couiPressable(
                    shape = CircleShape,
                    backgroundColor = colors.accent,
                    scaleOnPress = true,
                    spotlight = SpotlightPreset.OpaqueSmall1,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalCouiFallbackContentColor provides colors.onAccent) {
                content()
            }
        }
    }
}

/**
 * FAB 的图标重载：内容只有一枚图标时直接传图标，页面不必再包一层 Box。
 * [rotationDegrees] 供刷新、随机等需要状态感的动作使用。
 */
@Composable
fun CouiFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
) {
    CouiFab(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = rotationDegrees },
        )
    }
}

/**
 * 降级 FAB 的内容色：原厂 Fab 会自行下发 contentColor，低版本分支用这里补齐，
 * 页面写 Icon(tint = LocalCouiFallbackContentColor.current) 即可在两种实现下取同一颜色。
 */
val LocalCouiFallbackContentColor = staticCompositionLocalOf<Color> { Color.Unspecified }

/**
 * 胶囊分段选择：优先委托给 COUI 库官方 [SegmentedControl]（API 34+ 支持滑选、弹性阻尼与微光），
 * 低版本回落至等分胶囊实现。
 *
 * [backdrop] 默认为空，使用不透明分组底色，可安全放进滚动列表与卡片组内；
 * 传独立前景材质源时走透明模糊。[small] 对应库内 32dp 紧凑档（默认 40dp）。
 */
@Composable
fun CouiSegmentedCapsules(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
    backdrop: CouiBackdrop? = null,
) {
    if (items.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    if (Build.VERSION.SDK_INT >= 34) {
        CouiLibrarySegmentedControl(
            items = items,
            selectedIndex = clampedIndex,
            onSelect = onSelect,
            modifier = modifier,
            small = small,
            enabled = enabled,
            backdrop = backdrop,
        )
        return
    }
    CouiFallbackSegmentedCapsules(
        items = items,
        selectedIndex = clampedIndex,
        onSelect = onSelect,
        modifier = modifier,
        small = small,
        enabled = enabled,
    )
}

@RequiresApi(34)
@Composable
private fun CouiLibrarySegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    small: Boolean,
    enabled: Boolean,
    backdrop: CouiBackdrop?,
) {
    val renderer = remember(backdrop != null) {
        if (backdrop != null) runCatching { CouiFloatingRenderer.standalone() }.getOrNull() else null
    }
    val effectiveBackdrop = if (renderer != null) backdrop else null
    SegmentedControl(
        modifier = modifier.fillMaxWidth(),
        labels = items,
        selectedIndex = selectedIndex,
        onSelected = onSelect,
        backdrop = effectiveBackdrop,
        dark = CouiTheme.isDark,
        enabled = enabled,
        small = small,
        renderer = renderer,
    )
}

@Composable
private fun CouiFallbackSegmentedCapsules(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    small: Boolean,
    enabled: Boolean,
) {
    val colors: CouiColorScheme = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val labelStyle = rememberCouiTextStyle(CouiTextKind.ItemValue)
    val capsuleHeight = if (small) 32.dp else 40.dp
    val containerPadding = if (small) 2.dp else 4.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CouiCapsuleShape)
            .background(colors.container)
            .padding(horizontal = containerPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(containerPadding),
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val press = rememberCouiPressMotion()
            val capsuleInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (small) 40.dp else CouiMinTouchTarget)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = capsuleInteraction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(capsuleHeight)
                        .graphicsLayer {
                            scaleX = press.scale.value
                            scaleY = press.scale.value
                        }
                        .clip(RoundedCornerShape(20.dp))
                        // 未选中段也垫容器色：凝光要有不透明底才不会被页面底色吃掉
                        .background(
                            if (selected) {
                                colors.accent.copy(alpha = CouiSelectedContainerAlpha)
                            } else {
                                colors.container
                            },
                        )
                        .couiPressMotion(press = press, cancelOnConsumedMove = false)
                        .couiSpotlightLight(
                            preset = SpotlightPreset.ChipTab,
                            interactive = enabled,
                            color = if (selected) colors.accent else colors.onSurfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val textColor = if (selected) colors.accent else colors.onSurfaceVariant
                    BasicText(
                        text = label,
                        style = labelStyle,
                        color = { if (enabled) textColor else textColor.copy(alpha = 0.38f) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 按压即触觉：手指按下（[PressInteraction.Press]）时触发一次 COUI 触觉，而不是等抬起的点击。
 *
 * 供自己拼手势的组件使用（作品卡、融合滑动行、个性化横幅等）；[couiPressable] 已内置同一段逻辑，
 * 且同样保证一次按住只触发一次。ROM 不支持该触觉时库侧返回 false，这里不重试也不报错。
 */
@Composable
fun CouiPressHapticEffect(
    interactionSource: InteractionSource,
    feedback: CouiHapticFeedback = CouiHapticFeedback.Switch,
) {
    val view = LocalView.current
    LaunchedEffect(interactionSource, feedback, view) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                view.performCouiHapticFeedback(feedback)
            }
        }
    }
}

/** 顶栏刷新胶囊：信息流各页共用同一形态，避免每页各写一套。 */
@Composable
fun CouiFeedRefreshAction(
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val tint = if (refreshing) colors.onSurfaceVariant else colors.accent
    Row(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .couiPressable(
                shape = CouiCapsuleShape,
                spotlight = SpotlightPreset.ChipTab,
                enabled = !refreshing,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onRefresh,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = CouiIcons.Refresh, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = if (refreshing) CouiStrings.Refreshing else CouiStrings.Refresh, kind = CouiTextKind.GroupFooter, color = tint)
    }
}

/** 登录过期或未登录时的落点卡片：解释原因并提供明确的「去登录」入口。 */
@Composable
fun CouiFeedLoginGroup(
    message: String = CouiStrings.LoginExpired,
    modifier: Modifier = Modifier,
    title: String = CouiStrings.NeedLogin,
    onOpenLogin: () -> Unit,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val displayText = message.ifBlank { CouiStrings.LoginExpired }
    Group(
        modifier = modifier.padding(top = 8.dp),
        title = title,
        footer = displayText,
    ) {
        Item(
            title = {
                CouiBodyText(text = CouiStrings.OpenLogin, textAlign = TextAlign.Start)
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
            },
            onClick = onOpenLogin,
        )
    }
}

/**
 * 屏级失败块：会话失效给登录入口，其余给重试。
 *
 * 会话失效时「重试」只会再吃一次 401，因此入口直接换成 [CouiFeedLoginGroup]，
 * 不再把登录伪装成重试按钮；两者共用同一套「标题 + 说明 + 一行动作」形态。
 */
@Composable
fun CouiScreenFailureGroup(
    message: String?,
    expired: Boolean,
    onRetry: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expired) {
        CouiFeedLoginGroup(
            message = message.orEmpty().ifBlank { CouiStrings.LoginExpired },
            modifier = modifier,
            onOpenLogin = onOpenLogin,
        )
    } else {
        CouiErrorGroup(message = message.orEmpty(), onRetry = onRetry, modifier = modifier)
    }
}

/** 触底续页：滚动模式下列表末尾进入视口就拉下一页，失败时不自动重试。 */
@Composable
fun CouiAutoLoadMoreEffect(
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(feed.page, feed.loadingMore, feed.loadMoreFailed) {
        if (!feed.canLoadMore || feed.loadingMore || feed.loadMoreFailed) return@LaunchedEffect
        onLoadMore()
    }
}

/**
 * 统一的圆形头像与徽章：底色圆垫在下方，缺图、加载中或失败均不留透明空洞；
 * 支持传入昵称首字母占位。
 */
@Composable
fun CouiAvatar(
    url: String?,
    size: Dp = 40.dp,
    name: String? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription ?: name ?: "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else if (initial != null) {
            if (size >= 56.dp) {
                CouiSectionTitleText(
                    text = initial,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = initial,
                    kind = CouiTextKind.ItemTitle,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 回复线：深层回复贴在缩进区外侧的竖线，标识回复归属层级。 */
fun Modifier.drawReplyLine(depth: Int, color: Color): Modifier =
    if (depth <= 0) {
        this
    } else {
        drawBehind {
            val x = -6.dp.toPx()
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }

const val COUI_COMMENT_MAX_INDENT_DEPTH = 3

/**
 * 统一评论行组件：收拢相册详情、论坛、阅读器与小说详情的评论呈现。
 */
@Composable
fun CouiCommentRow(
    item: CommentItem,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    maxIndentDepth: Int = COUI_COMMENT_MAX_INDENT_DEPTH,
    authorColor: Color = CouiTheme.colorSchemeFor(CouiTheme.isDark).accent,
    avatarSize: Dp? = null,
    // 默认不截断：正文最长可见行数由各页按自己的信息密度传入，组件不替页面决定
    bodyMaxLines: Int = Int.MAX_VALUE,
    onReply: ((CommentItem) -> Unit)? = null,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val indentDepth = depth.coerceAtMost(maxIndentDepth)
    val resolvedAvatarSize = avatarSize ?: if (depth == 0) 40.dp else 30.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp + (indentDepth * 12).dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .drawReplyLine(depth = depth, color = colors.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CouiAvatar(
                url = item.avatarUrl,
                size = resolvedAvatarSize,
                name = item.author,
                contentDescription = item.author,
            )
            item.level?.let { level ->
                Text(
                    text = "Lv.$level",
                    kind = CouiTextKind.GroupFooter,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.author,
                        kind = CouiTextKind.ItemSummary,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.levelName?.takeIf { it.isNotBlank() }?.let { levelName ->
                        Text(
                            text = "· $levelName",
                            kind = CouiTextKind.GroupFooter,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (item.likes > 0) {
                    Text(
                        text = "赞 ${item.likes}",
                        kind = CouiTextKind.GroupFooter,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item.postDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = date,
                    kind = CouiTextKind.GroupFooter,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            }
            if (item.badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.badges.forEach { badge ->
                        CouiAvatar(
                            url = badge,
                            size = 22.dp,
                            contentDescription = "徽章",
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val segments = item.body.ifEmpty { listOf(CommentSegment.Text(item.content)) }
            Column(modifier = Modifier.fillMaxWidth()) {
                segments.forEach { segment ->
                    when (segment) {
                        is CommentSegment.Text -> if (segment.text.isNotBlank()) {
                            CouiBodyText(
                                text = segment.text,
                                color = colors.onSurface,
                                maxLines = bodyMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                            )
                        }
                        is CommentSegment.Album -> CouiBodyText(
                            text = segment.label,
                            color = colors.accent,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            onReply?.let { reply ->
                Text(
                    text = "回复",
                    kind = CouiTextKind.ItemValue,
                    color = colors.accent,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .clip(CouiCapsuleShape)
                        .couiPressable(
                            shape = CouiCapsuleShape,
                            spotlight = SpotlightPreset.ChipTab,
                            minTouchTarget = CouiMinTouchTarget,
                            onClick = { reply(item) },
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
