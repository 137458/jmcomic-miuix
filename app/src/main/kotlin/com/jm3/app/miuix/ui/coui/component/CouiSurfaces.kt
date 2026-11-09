// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.CouiType
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import com.jm3.app.miuix.ui.coui.theme.LocalCouiShapes
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.squircleClip
import kotlin.math.roundToInt

internal fun couiVisibleTags(
    tags: List<String>,
    expanded: Boolean,
    collapsedLimit: Int?,
): List<String> = if (collapsedLimit != null && !expanded) tags.take(collapsedLimit) else tags


/**
 * 按压缩放必须放在卡片最外层，才能连背景与投影一起缩小；
 * 覆盖层单独追加在内容之后，由卡片自身的 squircle 裁剪限制在圆角内。
 */
@Composable
private fun Modifier.couiPressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = CouiMotion.PressSpring,
        label = "couiPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun Modifier.couiPressOverlay(interaction: MutableInteractionSource): Modifier {
    val colors = LocalCouiColors.current
    val pressed by interaction.collectIsPressedAsState()
    val overlay by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = CouiMotion.PressSpring,
        label = "couiPressOverlay",
    )
    return drawWithContent {
        drawContent()
        if (overlay > 0f) {
            drawRect(
                if (colors.isDark) Color.White.copy(alpha = overlay) else Color.Black.copy(alpha = overlay),
            )
        }
    }
}

/**
 * 卡片级点击修饰符：按压缩放 + 按压覆盖层 + 点击语义。
 * 抽离为统一修饰符，避免 [CouiCard] 与 [CouiNestedCard] 重复实现或出现条件式 Composable 调用。
 */
@Composable
fun Modifier.couiCardClickable(
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    role: Role = Role.Button,
): Modifier {
    if (onClick == null) return this
    val interaction = remember { MutableInteractionSource() }
    return this
        .then(if (enabled) Modifier.couiPressScale(interaction) else Modifier)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        .then(if (enabled) Modifier.couiPressOverlay(interaction) else Modifier)
}

/**
 * 行级点击：补到 48dp 最小触摸目标，按压用 6% 覆盖层反馈，不画涟漪。
 *
 * 行是整宽文本行，按下不做缩放，避免文字随整行一起缩窄。
 */
@Composable
fun Modifier.couiClickable(
    onClick: () -> Unit,
    ensureMinTouchTarget: Boolean = true,
    enabled: Boolean = true,
    role: Role? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val base = if (ensureMinTouchTarget) defaultMinSize(minHeight = 48.dp) else this
    return base
        .then(if (enabled) Modifier.couiPressOverlay(interaction) else Modifier)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * 单选项/分段项：按压反馈与整行可点一致，选中态交给无障碍语义，读屏会播报「已选择」。
 */
@Composable
fun Modifier.couiSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.RadioButton,
    enabled: Boolean = true,
    ensureMinTouchTarget: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val base = if (ensureMinTouchTarget) defaultMinSize(minHeight = 48.dp) else this
    return base
        .then(if (enabled) Modifier.couiPressOverlay(interaction) else Modifier)
        .selectable(
            selected = selected,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * COUI 主卡片：皮肤卡片曲率的连续曲率、浅色双层投影、深色 1dp 描边。
 *
 * 不投影时仍靠描边与底色差保持层次，这是验收口径“无投影仍有层次”的落点。
 */
@Composable
fun CouiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalCouiColors.current
    val shapes = LocalCouiShapes.current
    val shape = RoundedCornerShape(shapes.card)
    Column(
        modifier = modifier
            .couiCardClickable(onClick = onClick, enabled = enabled)
            .then(
                if (colors.isDark) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.12f),
                    )
                },
            )
            .squircleClip(shapes.card)
            .background(colors.card)
            .then(if (colors.isDark) Modifier.border(1.dp, colors.cardBorder, shape) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** COUI 嵌套卡片：皮肤控件曲率 + 1dp 边框，用于主卡片里的分组行；支持可选点击与禁用态。 */
@Composable
fun CouiNestedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalCouiColors.current
    val shapes = LocalCouiShapes.current
    val shape = RoundedCornerShape(shapes.nested)
    Column(
        modifier = modifier
            .couiCardClickable(onClick = onClick, enabled = enabled)
            .squircleClip(shapes.nested)
            .background(colors.nestedCard)
            .border(1.dp, colors.cardBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 分组标题：17sp/600，统一 28dp 分组间距基线。 */
@Composable
fun CouiSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = CouiType.section.copy(color = LocalCouiColors.current.textPrimary),
        modifier = modifier.padding(top = 28.dp, bottom = 12.dp),
    )
}

/**
 * COUI 弹窗：页内浮层而非独立 Window。
 *
 * 独立 Window 的遮罩由系统 Dialog 决定，无法稳定压到 35%；页内浮层能直接控制遮罩、
 * 圆角与弹簧双向进出场（DialogSpring 入场，减速退场），也不受 NavDisplay 尺寸变化影响。
 * 返回键在浮层显示期间只关弹窗。
 */
@Composable
fun CouiDialogOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
    ) {
        BackHandler(onBack = onDismiss)
        val colors = LocalCouiColors.current
        val dialogRadius = LocalCouiShapes.current.dialog
        val shape = RoundedCornerShape(dialogRadius)
        val consumeInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(initialScale = 0.92f, animationSpec = CouiMotion.DialogSpring),
                        exit = scaleOut(targetScale = 0.92f, animationSpec = tween(140)),
                    )
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .shadow(
                        elevation = 28.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.14f),
                    )
                    .squircleClip(dialogRadius)
                    .background(colors.card)
                    .then(if (colors.isDark) Modifier.border(1.dp, colors.cardBorder, shape) else Modifier)
                    .clickable(
                        interactionSource = consumeInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(24.dp),
                content = content,
            )
        }
    }
}

/** COUI 主按钮：44dp 高、皮肤控件曲率、绿色填充，附带弹簧微缩按压反馈；绿色只用于这一个明确动作。 */
@Composable
fun CouiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalCouiColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(44.dp)
            .then(if (enabled) Modifier.couiPressScale(interaction) else Modifier)
            .squircleClip(LocalCouiShapes.current.control)
            .background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.38f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier.couiPressOverlay(interaction) else Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = CouiType.button.copy(color = colors.onAccent.copy(alpha = if (enabled) 1f else 0.7f)),
        )
    }
}

/** COUI 文字按钮：用于「取消 / 清除 / 更多」这类不占主视觉的动作；支持颜色定制与禁用态。 */
@Composable
fun CouiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = LocalCouiColors.current.accent,
) {
    val colors = LocalCouiColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(44.dp)
            .squircleClip(LocalCouiShapes.current.control)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier.couiPressOverlay(interaction) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = CouiType.button.copy(color = if (enabled) color else colors.textTertiary),
        )
    }
}

/** COUI 胶囊标签：热门标签与筛选复用，选中态用绿色容器，支持按压反馈、可选前导图标与禁用态。 */
@Composable
fun CouiChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = LocalCouiColors.current
    val shape = RoundedCornerShape(LocalCouiShapes.current.pill)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(if (enabled) Modifier.couiPressScale(interaction) else Modifier)
            .clip(shape)
            .background(
                if (!enabled) colors.card.copy(alpha = 0.5f)
                else if (selected) colors.accentContainer
                else colors.card,
            )
            .border(
                1.dp,
                if (!enabled) colors.cardBorder.copy(alpha = 0.5f)
                else if (selected) colors.accent.copy(alpha = 0.25f)
                else colors.cardBorder,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier.couiPressOverlay(interaction) else Modifier)
            .padding(horizontal = if (leadingIcon != null) 10.dp else 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (!enabled) colors.textTertiary else if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
        }
        BasicText(
            text = text,
            style = CouiType.caption.copy(
                color = if (!enabled) colors.textTertiary else if (selected) colors.accent else colors.textSecondary,
            ),
        )
    }
}

/**
 * COUI 底部面板：顶部取皮肤面板曲率 + 32×4 拖拽条，支持双向滑动进退场动画与平滑弹簧回弹。
 * 针对大屏与横屏增加最大宽度收口（封顶 560dp 居中），避免横向过度拉伸。
 */
@Composable
fun CouiBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160)),
    ) {
        BackHandler(onBack = onDismiss)
        val colors = LocalCouiColors.current
        val sheetShape = LocalCouiShapes.current.sheet
        val consumeInteraction = remember { MutableInteractionSource() }
        val offsetY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = modifier
                    .animateEnterExit(
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = CouiMotion.SheetOffsetSpring,
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(180, easing = CouiMotion.PushEasing),
                        ),
                    )
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(topStart = sheetShape, topEnd = sheetShape))
                    .background(colors.card)
                    .clickable(
                        interactionSource = consumeInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val threshold = with(density) { 60.dp.toPx() }
                                    if (offsetY.value > threshold) {
                                        onDismiss()
                                    }
                                    scope.launch {
                                        offsetY.animateTo(0f, animationSpec = CouiMotion.SheetSpring)
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        offsetY.animateTo(0f, animationSpec = CouiMotion.SheetSpring)
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    scope.launch {
                                        offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                                    }
                                },
                            )
                        }
                        .padding(top = 10.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(colors.textTertiary.copy(alpha = 0.6f), CircleShape),
                    )
                }
                content()
            }
        }
    }
}

/**
 * COUI 文本输入框：统一边框、背景、圆角与聚焦绿环。
 *
 * 支持单行与多行，可用于评论输入、回复输入以及自定义线路域名输入等场景。
 */
@Composable
fun CouiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Dp = 48.dp,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalCouiColors.current
    val control = LocalCouiShapes.current.control
    val shape = RoundedCornerShape(control)
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .squircleClip(control)
            .background(colors.searchField)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.accent.copy(alpha = 0.4f) else colors.searchBorder,
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            BasicText(
                text = placeholder,
                style = CouiType.body.copy(color = colors.textTertiary),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .onFocusChanged { focused = it.isFocused },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = CouiType.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}

/** 页内提示卡：错误/空态/失效共用；包含说明文字与可选操作按钮与可选图标。 */
@Composable
fun CouiNoticeCard(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalCouiColors.current
    CouiCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                )
            }
            BasicText(
                text = message,
                style = CouiType.body.copy(color = colors.textSecondary),
                modifier = Modifier.weight(1f),
            )
        }
        if (actionText != null && onAction != null) {
            CouiTextButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** COUI 设置分组：17sp 分组标题 + squircle 主卡片，卡内统一 16dp 水平内衬。 */
@Composable
fun CouiSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiSectionTitle(
        text = title,
        modifier = modifier.padding(horizontal = 16.dp),
    )
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        content = content,
    )
}

/**
 * COUI 设置行：标题 + 辅助说明 + 可选右侧值。
 *
 * 水平内衬由外层 CouiCard 提供，这里只补纵向间距与可点交互。
 */
@Composable
fun CouiSettingsRow(
    title: String,
    summary: String = "",
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalCouiColors.current
    val titleColor = if (enabled) colors.textPrimary else colors.textTertiary
    val summaryColor = if (enabled) colors.textSecondary else colors.textTertiary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.couiClickable(onClick = onClick, enabled = enabled)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = CouiType.bodyEmphasis.copy(color = titleColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotEmpty()) {
                BasicText(
                    text = summary,
                    style = CouiType.caption.copy(color = summaryColor),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        value?.let {
            BasicText(
                text = it,
                style = CouiType.caption.copy(color = colors.textTertiary),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        trailing?.invoke()
    }
}

/**
 * COUI 分隔线：默认高度 1dp，取主题分隔线色。
 */
@Composable
fun CouiDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalCouiColors.current.divider,
    thickness: Dp = 1.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

/**
 * COUI 紧凑作品卡片：3:4 比例封面 + 2 行标题 + 1 行副标题/作者。
 * 供首页连载更新、随便看看与详情页相关作品推荐复用。
 */
@Composable
fun CouiCompactWorkCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(112.dp),
) {
    val colors = LocalCouiColors.current
    CouiCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(colors.accentContainer),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            BasicText(
                text = title,
                style = CouiType.cardTitle.copy(color = colors.textPrimary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                BasicText(
                    text = subtitle,
                    style = CouiType.captionMeta.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * COUI 标签流：自动换行排列的胶囊集合，支持展开/收起过渡动画。
 * 供首页热门标签与详情页作品标签复用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CouiTagFlow(
    tags: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsedLimit: Int? = null,
    expanded: Boolean = true,
) {
    val visibleTags = couiVisibleTags(tags, expanded, collapsedLimit)
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = CouiMotion.ListSizeSpring),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleTags.forEach { tag ->
            CouiChip(text = tag, onClick = { onClick(tag) })
        }
    }
}
