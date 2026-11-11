// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.graphics.BlendMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.effect.couiButtonFeedback
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
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.bottomBarShape
import com.jm3.core.paging.PagedFeed
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import top.yukonga.miuix.kmp.basic.Icon

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
    content: @Composable (PaddingValues) -> Unit,
) {
    // 净空值不含系统导航栏 inset，占位也要让出同一条底部安全区
    val barClearance = LocalFloatingBarContentPadding.current
    // 库 Scaffold 的 windowInsets 只含系统栏，IME 避让按库要求由宿主缩小视口（Scaffold.kt:45）
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = topBar,
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
        content = content,
    )
}

/** 可点件最小触摸目标：无障碍基线 48dp，低于它的可见按钮靠留白补足触区。 */
internal val CouiMinTouchTarget = 48.dp

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
 * @param minTouchTarget 补足触区的最小边长；调用方把本修饰符写在固定尺寸之前，
 * 视觉内容仍按原尺寸居中，只有可点范围变大。
 * @param scaleOnPress 小型控件（标签、图标键、胶囊）打开原厂缩放；整行列表项保持关闭。
 */
@Composable
fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape? = null,
    minTouchTarget: Dp = 0.dp,
    scaleOnPress: Boolean = false,
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
    val visualFeedback = if (shape != null) {
        Modifier
            .clip(shape)
            .couiButtonFeedback(
                dark = CouiTheme.isDark,
                interactionSource = interactionSource,
                enabled = enabled,
                list = false,
            )
    } else {
        Modifier.couiButtonFeedback(
            dark = CouiTheme.isDark,
            interactionSource = interactionSource,
            enabled = enabled,
            list = false,
        )
    }
    val scaledFeedback = if (press != null) {
        visualFeedback
            .couiPressMotion(press = press, enabled = enabled, cancelOnConsumedMove = true)
            .graphicsLayer {
                scaleX = press.scale.value
                scaleY = press.scale.value
            }
    } else {
        visualFeedback
    }

    return this
        .then(scaledFeedback)
        .couiMinimumTouchTarget(minTouchTarget)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
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
                Text(text = text, kind = CouiTextKind.Body, textAlign = TextAlign.Start)
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
                Text(text = text, kind = CouiTextKind.Body, textAlign = TextAlign.Start)
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
    Group(modifier = modifier, title = "加载失败", footer = message) {
        Item(
            title = {
                Text(text = "重试", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
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
                    Text(text = "重新加载", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
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
                    Text(text = "正在加载更多…", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
                },
            )
        }

        endReached -> Group(modifier = modifier, footer = "没有更多了") {
            Item(
                title = {
                    Text(text = "已经到底了", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
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
    errorMessage: String,
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

/** 悬浮档视觉间隙：条身与屏幕底边之间的留白（系统导航栏 inset 另计）。 */
private val CouiBarVisualGap = 8.dp

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
internal fun couiUsesLibraryNavBar(tokens: GlassBottomBarTokens, sdkInt: Int): Boolean =
    tokens.size == BottomBarSize.STANDARD &&
        tokens.form == BottomBarForm.FLOATING &&
        sdkInt >= CouiLibraryNavBarMinSdk

/**
 * COUI 底栏：自研无黑块阴影、支持连续曲率胶囊裁剪与原厂弹性缩放反馈的纯净底栏。
 *
 * 库底栏存在严重渲染缺陷（其私有函数 FloatingNode 硬编码了未裁剪的 Spotlight，在 API 34+
 * 按压时通过 Canvas.drawPaint 必现未受限的直角黑色方块阴影）。统一走此自研底栏，
 * 消除第三方库凝光直角黑块与 Compose 默认未裁剪矩形 Ripple。
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
    CouiPlainNavigationBar(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        tokens = tokens,
        modifier = modifier,
    )
}

/** 库浮动材质底栏保留为内部后备。 */
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
    val shape = bottomBarShape(tokens)
    val tabShape = RoundedCornerShape(tokens.indicatorHeight / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = tokens.horizontalMargin,
                end = tokens.horizontalMargin,
                top = if (tokens.floating) CouiBarVisualGap else 0.dp,
                bottom = if (tokens.floating) CouiBarVisualGap else 0.dp,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = (if (tokens.floating) Modifier.widthIn(max = 540.dp) else Modifier)
                .fillMaxWidth()
                .then(
                    if (tokens.floating) {
                        Modifier.dropShadow(
                            shape = shape,
                            shadow = Shadow(
                                radius = CouiBarShadowRadius,
                                color = Color.Black,
                                alpha = if (CouiTheme.isDark) tokens.shadowAlphaDark else tokens.shadowAlphaLight,
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(colors.container)
                .height(tokens.height)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val press = rememberCouiPressMotion()
                val interactionSource = remember { MutableInteractionSource() }
                val (lightInput, drawLight) = rememberCouiSpotlight(
                    interactive = true,
                    preset = SpotlightPreset.ChipTab,
                    dark = CouiTheme.isDark,
                    blendMode = if (CouiTheme.isDark) BlendMode.PLUS else BlendMode.SRC_OVER,
                    cancelOnConsumedMove = true,
                )

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
                        .background(
                            if (selected) {
                                colors.accent.copy(alpha = CouiSelectedContainerAlpha)
                            } else {
                                Color.Transparent
                            },
                        )
                        .then(lightInput)
                        .drawBehind {
                            drawLight?.invoke(this)
                        }
                        .couiPressMotion(press = press, cancelOnConsumedMove = true)
                        .selectable(
                            selected = selected,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = if (selected) null else item.title,
                            modifier = Modifier.size(tokens.iconSize),
                            tint = if (selected) colors.accent else colors.onSurfaceVariant,
                        )
                        if (selected) {
                            Spacer(Modifier.width(6.dp))
                            BasicText(
                                text = item.title,
                                style = TextStyle(
                                    fontSize = tokens.labelSize,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = { colors.accent },
                                maxLines = 1,
                            )
                        }
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
            val (lightInput, drawLight) = rememberCouiSpotlight(
                interactive = true,
                preset = SpotlightPreset.ChipTab,
                dark = CouiTheme.isDark,
                blendMode = if (CouiTheme.isDark) BlendMode.PLUS else BlendMode.SRC_OVER,
                cancelOnConsumedMove = true,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiRailItemMargin)
                    .graphicsLayer {
                        scaleX = press.scale.value
                        scaleY = press.scale.value
                    }
                    .clip(CouiCapsuleShape)
                    .background(if (selected) colors.accent.copy(alpha = CouiSelectedContainerAlpha) else Color.Transparent)
                    .then(lightInput)
                    .drawBehind {
                        drawLight?.invoke(this)
                    }
                    .couiPressMotion(press = press, cancelOnConsumedMove = true)
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
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onClick),
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
 * 胶囊分段选择：页面内切换用，替代需要独立背景源的 ChipTabRow。
 *
 * 选中态取强调色容器，未选中态取分组容器色，高度与 COUI 行高对齐。
 * 反馈按库的 ChipTab 预设拼：每段一个有界触区 + 原厂按压缩放 + 触点点亮
 * （[SpotlightPreset.ChipTab]），文本走 [CouiTextKind.PreferenceValue] 档，不再写字面量字号。
 */
@Composable
fun CouiSegmentedCapsules(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors: CouiColorScheme = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val labelStyle = rememberCouiTextStyle(CouiTextKind.PreferenceValue)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CouiCapsuleShape)
            .background(colors.container)
            .padding(horizontal = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val press = rememberCouiPressMotion()
            val (lightInput, drawLight) = rememberCouiSpotlight(
                interactive = true,
                preset = SpotlightPreset.ChipTab,
                dark = CouiTheme.isDark,
                cancelOnConsumedMove = true,
            )
            val capsuleInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 触区按 48dp 起算，胶囊本体 40dp 居中；容器只留横向内衬，段高不再凑数
                    .height(CouiMinTouchTarget)
                    .couiPressMotion(press = press, cancelOnConsumedMove = true)
                    .then(lightInput)
                    .selectable(
                        selected = selected,
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
                        .height(40.dp)
                        .graphicsLayer {
                            scaleX = press.scale.value
                            scaleY = press.scale.value
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) {
                                colors.accent.copy(alpha = CouiSelectedContainerAlpha)
                            } else {
                                Color.Transparent
                            },
                        )
                        .drawWithContent {
                            drawContent()
                            drawLight?.invoke(this)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = label,
                        style = labelStyle,
                        color = { if (selected) colors.accent else colors.onSurfaceVariant },
                        maxLines = 1,
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
