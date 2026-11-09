// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.LocalCouiShapes
import com.jm3.app.miuix.ui.coui.theme.CouiType
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import com.jm3.app.miuix.ui.glass.LocalGlassBottomBarTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * COUI 顶栏：56dp 内容区 + 状态栏 inset。
 *
 * 未滚动时完全透明，让内容（封面/大标题）直接透到状态栏；滚动后才淡入 24dp 模糊与
 * 82% 表面色，并出现 1dp 6% 分隔线。这一条对应验收口径“顶栏仅滚动后模糊”。
 */
@Composable
fun CouiTopBar(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalCouiColors.current
    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(CouiMotion.TopBarFadeMs),
        label = "couiTopBarScrim",
    )
    Box(modifier = modifier.fillMaxWidth()) {
        if (scrimAlpha > 0.001f) {
            val backgroundModifier = if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(24.dp.toPx(), 24.dp.toPx()) },
                    onDrawSurface = { drawRect(colors.topBarSurface.copy(alpha = 0.82f)) },
                )
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            colors.topBarSurface.copy(alpha = 0.86f),
                            colors.topBarSurface.copy(alpha = 0.82f),
                        ),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .then(backgroundModifier),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            BasicText(
                text = title,
                style = CouiType.topBar.copy(color = colors.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (navigation == null) 8.dp else 0.dp),
            )
            actions()
        }
        if (scrimAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.textPrimary.copy(alpha = 0.06f * scrimAlpha)),
            )
        }
    }
}

data class CouiNavItem(val label: String, val icon: ImageVector)

data class CouiBadge(val count: Int? = null, val dot: Boolean = false)

/** 悬浮底栏大屏封顶宽度；与 HyperOS 侧液态玻璃底栏保持同一档。 */
private val CouiFloatingBarMaxWidth = 540.dp

/**
 * COUI 底栏：几何尺寸与形态完全取自 [LocalGlassBottomBarTokens]。
 *
 * 悬浮档按内容宽度收口（大屏封顶 540dp 居中）+ 皮肤卡片曲率 + 横向边距 + 轻投影；
 * 贴底档横向撑满、顶部 1dp 8% 分隔线、无投影。
 * 两种形态都不做折射与边缘白高光——这是与 HyperOS 液态玻璃的核心差异。
 * 高度、图标与字号随「普通/偏大」档变化，不再写死 56dp。
 */
@Composable
fun CouiBottomBar(
    items: List<CouiNavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    badge: (Int) -> CouiBadge? = { null },
) {
    val colors = LocalCouiColors.current
    val tokens = LocalGlassBottomBarTokens.current
    val barColor = colors.barSurface.copy(alpha = 0.78f)
    val shadowAlpha = if (colors.isDark) tokens.shadowAlphaDark else tokens.shadowAlphaLight
    if (tokens.floating) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = tokens.horizontalMargin, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(LocalCouiShapes.current.card)
            val backgroundModifier = if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = { blur(16.dp.toPx(), 16.dp.toPx()) },
                    onDrawSurface = { drawRect(barColor) },
                )
            } else {
                Modifier.background(barColor)
            }
            Row(
                modifier = Modifier
                    // 悬浮档封顶 540dp 并居中；在可用宽度内均分，绝不超出设备显示区域
                    .fillMaxWidth()
                    .widthIn(max = CouiFloatingBarMaxWidth)
                    .height(tokens.height)
                    .shadow(
                        elevation = 8.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = shadowAlpha),
                        spotColor = Color.Black.copy(alpha = shadowAlpha),
                    )
                    .clip(shape)
                    .then(backgroundModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItems(items, selectedIndex, onItemClick, badge, tokens.iconSize, tokens.labelSize)
            }
        }
        return
    }
    Box(modifier = modifier.fillMaxWidth()) {
        val backgroundModifier = if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(16.dp.toPx(), 16.dp.toPx()) },
                onDrawSurface = { drawRect(barColor) },
            )
        } else {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        colors.barSurface.copy(alpha = 0.84f),
                        colors.barSurface.copy(alpha = 0.78f),
                    ),
                ),
            )
        }
        Box(Modifier.matchParentSize().then(backgroundModifier))
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.textPrimary.copy(alpha = 0.08f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.height),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItems(items, selectedIndex, onItemClick, badge, tokens.iconSize, tokens.labelSize)
            }
        }
    }
}

@Composable
private fun RowScope.NavItems(
    items: List<CouiNavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    badge: (Int) -> CouiBadge?,
    iconSize: Dp,
    labelSize: TextUnit,
) {
    items.forEachIndexed { index, item ->
        val colors = LocalCouiColors.current
        val selected = index == selectedIndex
        val pillAlpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = CouiMotion.TabSpring,
            label = "couiNavPill",
        )
        val iconTint by animateColorAsState(
            targetValue = if (selected) colors.accent else colors.textSecondary,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
            label = "couiNavTint",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .couiSelectable(
                    selected = selected,
                    role = Role.Tab,
                    ensureMinTouchTarget = false,
                    onClick = { onItemClick(index) },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val itemBadge = badge(index)
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .widthIn(min = iconSize + 24.dp)
                        .height(iconSize + 8.dp)
                        .squirclePill(pillAlpha),
                )
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
                BadgeMark(itemBadge, Modifier.align(Alignment.TopEnd))
            }
            BasicText(
                text = item.label,
                style = CouiType.tab.copy(
                    fontSize = labelSize,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                ),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** 选中胶囊：图标高 + 8dp、绿色 10%，随页签 spring 淡入淡出。 */
@Composable
private fun Modifier.squirclePill(alpha: Float): Modifier {
    val colors = LocalCouiColors.current
    return this
        .graphicsLayer { this.alpha = alpha }
        .squircleClip(LocalCouiShapes.current.pill)
        .background(colors.accent.copy(alpha = 0.10f))
}

@Composable
private fun BadgeMark(badge: CouiBadge?, modifier: Modifier = Modifier) {
    if (badge == null) return
    val colors = LocalCouiColors.current
    if (badge.count != null) {
        Box(
            modifier = modifier
                .offset(x = 5.dp, y = (-3).dp)
                .background(colors.accent, CircleShape)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            BasicText(
                text = if (badge.count > 99) "99+" else badge.count.toString(),
                style = CouiType.tab.copy(fontSize = 10.sp, color = colors.onAccent),
            )
        }
    } else if (badge.dot) {
        Box(
            modifier = modifier
                .offset(x = 2.dp, y = (-1).dp)
                .size(6.dp)
                .background(colors.accent, CircleShape),
        )
    }
}

/**
 * COUI 搜索框：40dp 高、皮肤卡片曲率、92% 表面色 + 16dp 模糊、1dp 描边、聚焦 2dp 绿 40%。
 * 只有一层模糊，内部不再叠任何玻璃层。
 */
@Composable
fun CouiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    leadingIcon: ImageVector? = null,
    onSearch: () -> Unit = {},
) {
    val colors = LocalCouiColors.current
    var focused by remember { mutableStateOf(false) }
    val searchRadius = LocalCouiShapes.current.search
    val shape = RoundedCornerShape(searchRadius)
    val fillColor = colors.searchField.copy(alpha = 0.92f)
    val backgroundModifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(16.dp.toPx(), 16.dp.toPx()) },
            onDrawSurface = { drawRect(fillColor) },
        )
    } else {
        Modifier.background(fillColor)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .squircleClip(searchRadius)
            .then(backgroundModifier)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.accent.copy(alpha = 0.4f) else colors.searchBorder,
                shape = shape,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                BasicText(
                    text = placeholder,
                    style = CouiType.body.copy(color = colors.textTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                textStyle = CouiType.body.copy(color = colors.textPrimary),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onValueChange("") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "×",
                    style = CouiType.body.copy(color = colors.textSecondary, fontSize = 18.sp),
                )
            }
        }
    }
}

/**
 * COUI 分段控件：主题模式与搜索排序共用。
 * 选中胶囊用 spring(.78,560) 换色，不做滑块位移，避免长文案下宽度抖动。
 */
@Composable
fun CouiSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCouiColors.current
    val shapes = LocalCouiShapes.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .squircleClip(shapes.control)
            .background(colors.accentContainer.copy(alpha = if (colors.isDark) 1f else 0.7f))
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) colors.accent else Color.Transparent,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
                label = "couiSegment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .squircleClip(shapes.control)
                    .background(background)
                    // 分段项本身约 34dp，撑到 48dp 会把整条排序行撑高，触摸区域就按行高走
                    .couiSelectable(
                        selected = selected,
                        role = Role.Tab,
                        ensureMinTouchTarget = false,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label,
                    style = CouiType.button.copy(
                        color = if (selected) colors.onAccent else colors.textSecondary,
                    ),
                )
            }
        }
    }
}

/** 供页面统一取顶部净空：56dp 顶栏 + 状态栏。 */
@Composable
fun couiTopBarHeight(): Dp =
    56.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * COUI 浮动操作按钮（FAB）：44dp 方卡片，支持图标与旋转角度（如回到顶部、随机漫游）。
 */
@Composable
fun CouiFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
) {
    CouiCard(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotationDegrees },
            )
        }
    }
}
