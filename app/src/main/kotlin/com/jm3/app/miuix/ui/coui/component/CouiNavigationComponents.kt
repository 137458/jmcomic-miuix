// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.jm3.app.miuix.ui.MiuixDockedNavItem
import com.jm3.app.miuix.ui.MiuixDockedNavigationBar
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.bottomBarShape
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults

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
internal val CouiRailWidth = 88.dp

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
                            modifier = Modifier.size(couiScaledSize(tokens.iconSize)),
                            tint = if (selected) selectedColor else unselectedColor,
                        )
                        Spacer(Modifier.height(CouiNavLabelGap))
                        BasicText(
                            text = item.title,
                            style = TextStyle(
                                fontSize = couiScaledFontSize(tokens.labelSize),
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
                    modifier = Modifier.size(couiScaledSize(tokens.iconSize)),
                    tint = if (selected) colors.accent else colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(CouiRailLabelSpacing))
                BasicText(
                    text = item.title,
                    style = TextStyle(
                        fontSize = couiScaledFontSize(tokens.labelSize),
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
