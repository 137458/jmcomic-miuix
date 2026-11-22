// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.theme.CouiTheme as LegacyCouiTheme
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ButtonMaterial
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.MaterialBackdropSpec
import io.github.suqi8.coui.kmp.effect.couiFocusStroke
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogButtonBarDefaults
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.SinkFeedback
import io.github.suqi8.coui.kmp.utils.couiTouchFeedback
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

private const val ActionProgressAnimMs = 250
private const val ActionIndeterminateAnimMs = 1200

/** 在交互源按下瞬间触发一次 COUI 开关级触觉反馈。 */
@Composable
private fun CouiSwitchHapticEffect(
    interactionSource: InteractionSource,
    enabled: Boolean,
) {
    val haptic = rememberCouiHaptic()
    val currentHaptic by rememberUpdatedState(haptic)
    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) return@LaunchedEffect
        var down = false
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (!down) {
                        down = true
                        currentHaptic(CouiHapticEffect.Switch)
                    }
                }

                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> down = false
            }
        }
    }
}

/**
 * 破坏性确认按钮（旧版弹窗过渡垫片，Wave 3 移除；新弹窗请使用 [couiDestructiveDialogAction]）。
 */
@Deprecated("Wave 3 removal")
@Composable
fun couiDestructiveConfirmAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
): CouiDialogAction {
    val destructive = CouiDialogDefaults.themeColors(LegacyCouiTheme.isDark).destructive
    return CouiDialogAction(
        text = text,
        enabled = enabled,
        style = CouiDialogActionStyle.Primary,
        colors = CouiDialogDefaults.primaryActionColors(destructive),
        onClick = onClick,
    )
}

/**
 * 破坏性弹窗动作（suqi8/coui）：用于 [io.github.suqi8.coui.kmp.layout.DialogButtonBar] 的 `neutral` 槽，
 * 由库统一渲染为破坏性错误色。
 */
fun couiDestructiveDialogAction(
    text: String = "删除",
    enabled: Boolean = true,
    onClick: () -> Unit,
): DialogButtonBarAction = DialogButtonBarAction(
    text = text,
    enabled = enabled,
    recommended = false,
    onClick = onClick,
)

/**
 * 详情页主阅读大按钮：全宽主题高亮圆角 CTA，圆角与下方次级功能瓦片统一为 16dp。
 */
@Composable
fun CouiPrimaryActionButton(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = CouiDetailButtonShape,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val contentColor = if (enabled) scheme.onPrimary else scheme.labelSecondary
    val cornerRadius = if (shape === CouiDetailButtonShape || shape === CouiCommentFieldShape) {
        16.dp
    } else {
        ButtonDefaults.CornerRadius
    }
    val interactionSource = remember { MutableInteractionSource() }
    CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = enabled)

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CouiMinTouchTarget),
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = 0.dp,
        minHeight = CouiMinTouchTarget,
        colors = ButtonDefaults.buttonColorsPrimary(
            color = scheme.primary,
            disabledColor = scheme.cardBackground,
            contentColor = scheme.onPrimary,
            disabledContentColor = scheme.labelSecondary,
        ),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        materialBackdrop = false,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = COUITheme.textStyles.headlineXS.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = COUITheme.textStyles.bodyS,
                        color = contentColor.copy(alpha = if (enabled) 0.86f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor.copy(alpha = if (enabled) 0.9f else 0.7f),
                )
            }
        }
    }
}

/**
 * 详情页功能栏等宽瓦片：上方图标 + 下方状态文案的实底结构，放在分组卡片内承载收藏、喜欢、追更、下载等功能。
 */
@Composable
fun CouiDetailActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    val bgColor = when {
        active && dark -> lerp(scheme.cardBackground, scheme.primary, 0.24f)
        active -> lerp(scheme.card, scheme.primary, 0.14f)
        dark -> lerp(scheme.cardBackground, Color.White, 0.06f)
        else -> scheme.backgroundWithCard
    }
    val fgColor = when {
        active -> scheme.primaryText
        enabled || onClick == null -> scheme.labelPrimary
        else -> scheme.labelSecondary
    }
    val borderColor = when {
        active -> scheme.primary.copy(alpha = if (dark) 0.34f else 0.22f)
        dark -> scheme.labelPrimary.copy(alpha = 0.12f)
        else -> scheme.labelPrimary.copy(alpha = 0.06f)
    }
    val cornerRadius = 16.dp
    val tileShape = remember { SquircleShape(cornerRadius, COUIRoundCorner.Large.Weight) }
    val interactionSource = remember { MutableInteractionSource() }
    if (onClick != null) {
        CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = enabled)
    }

    val interactiveModifier = if (onClick != null) {
        Modifier
            .couiFocusStroke(
                interactionSource = interactionSource,
                shape = tileShape,
                enabled = enabled,
            )
            .indication(interactionSource, SinkFeedback())
            .squircleSurface(
                color = bgColor,
                cornerRadius = cornerRadius,
                weight = COUIRoundCorner.Large.Weight,
            )
            .squircleBorder(
                width = 0.5.dp,
                color = borderColor,
                cornerRadius = cornerRadius,
                weight = COUIRoundCorner.Large.Weight,
            )
            .couiTouchFeedback(
                interactionSource = interactionSource,
                enabled = enabled,
            )
            .indication(interactionSource, LocalIndication.current)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
            .squircleSurface(
                color = bgColor,
                cornerRadius = cornerRadius,
                weight = COUIRoundCorner.Large.Weight,
            )
            .squircleBorder(
                width = 0.5.dp,
                color = borderColor,
                cornerRadius = cornerRadius,
                weight = COUIRoundCorner.Large.Weight,
            )
    }

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .then(interactiveModifier)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = fgColor,
        )
        Text(
            text = label,
            style = COUITheme.textStyles.bodyXS,
            color = fgColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 分组标题栏右侧统一胶囊动作：用于「展开」「查看全部」「清除」「复制」「全部展开」。
 *
 * 保持紧致高度而不把 48dp minTouchTarget 撑进标题行内，全站所有分区标题角标风格一致；
 * 若外层通过 [LocalMaterialBackdrop] 提供材质背景源，则自动启用实时毛玻璃折射。
 */
@Composable
fun CouiHeaderCapsuleAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    tinted: Boolean = true,
    @Suppress("UNUSED_PARAMETER") backdrop: CouiBackdrop? = null,
) {
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    val bgColor = when {
        tinted -> scheme.primary.copy(alpha = if (dark) 0.18f else 0.12f)
        dark -> scheme.labelPrimary.copy(alpha = 0.09f)
        else -> scheme.labelPrimary.copy(alpha = 0.06f)
    }
    val borderColor = when {
        tinted -> scheme.primary.copy(alpha = if (dark) 0.34f else 0.20f)
        dark -> scheme.labelPrimary.copy(alpha = 0.14f)
        else -> scheme.labelPrimary.copy(alpha = 0.08f)
    }
    val fgColor = if (tinted) scheme.primaryText else scheme.labelSecondary
    val interactionSource = remember { MutableInteractionSource() }
    CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = true)

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 28.dp)
            .squircleBorder(
                width = 0.5.dp,
                color = borderColor,
                cornerRadius = COUIRoundCorner.Full,
                weight = COUIRoundCorner.CapsuleWeight,
            ),
        cornerRadius = ButtonDefaults.CornerRadius,
        minWidth = 0.dp,
        minHeight = 28.dp,
        colors = ButtonDefaults.buttonColors(
            color = bgColor,
            disabledColor = bgColor,
            contentColor = fgColor,
            disabledContentColor = scheme.disabledNeutral,
        ),
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
        material = ButtonMaterial.Secondary,
        interactionSource = interactionSource,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = text,
                style = COUITheme.textStyles.description,
                color = fgColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = fgColor,
                )
            }
        }
    }
}

/**
 * 统一标签/热词胶囊：自适应底色 + 0.5dp 凝光细边，深浅模式均能与卡片及玻璃弹窗拉开层次。
 *
 * 不叠加 48dp 外扩布局占位，保证 FlowRow 多行排布时视觉行距真实落在 Arrangement 设定值上。
 */
@Composable
fun CouiTagCapsulePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    highlighted: Boolean = false,
    glassSurface: Boolean = false,
) {
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    val bgColor = when {
        highlighted -> scheme.primary.copy(alpha = if (dark) 0.18f else 0.11f)
        glassSurface -> scheme.labelPrimary.copy(alpha = if (dark) 0.10f else 0.06f)
        dark -> scheme.labelPrimary.copy(alpha = 0.09f)
        else -> lerp(scheme.backgroundWithCard, scheme.card, 0.28f)
    }
    val borderColor = when {
        highlighted -> scheme.primary.copy(alpha = if (dark) 0.34f else 0.22f)
        dark -> scheme.labelPrimary.copy(alpha = 0.14f)
        else -> scheme.labelPrimary.copy(alpha = 0.08f)
    }
    val textColor = if (highlighted) scheme.primaryText else scheme.labelPrimary
    val minHeight = if (compact) 30.dp else 34.dp
    val interactionSource = remember { MutableInteractionSource() }
    CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = true)

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = minHeight)
            .squircleBorder(
                width = 0.5.dp,
                color = borderColor,
                cornerRadius = COUIRoundCorner.Full,
                weight = COUIRoundCorner.CapsuleWeight,
            ),
        cornerRadius = ButtonDefaults.CornerRadius,
        minWidth = 0.dp,
        minHeight = minHeight,
        colors = ButtonDefaults.buttonColors(
            color = bgColor,
            disabledColor = bgColor,
            contentColor = textColor,
            disabledContentColor = scheme.disabledNeutral,
        ),
        insideMargin = PaddingValues(
            horizontal = if (compact) 11.dp else 14.dp,
            vertical = if (compact) 5.dp else 7.dp,
        ),
        materialBackdrop = false,
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            style = if (compact) COUITheme.textStyles.description else COUITheme.textStyles.bodyS,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 整行开关：Switch 传 null 回调后只呈现状态、不再自带 toggleable，
 * 点击、开关角色与开/关播报由这里统一承担；[couiTouchFeedback] 与 [LocalIndication] 共用同一个
 * interactionSource，按压反馈即时生效。
 */
@Composable
internal fun Modifier.couiSwitchRow(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = enabled)
    return this
        .couiTouchFeedback(
            interactionSource = interactionSource,
            enabled = enabled,
        )
        .indication(interactionSource, LocalIndication.current)
        .toggleable(
            value = value,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onValueChange,
        )
}

/**
 * 与 COUI 原生 `DialogButtonBar` 次级按键等价的弹窗辅助按钮：
 * 沿用 [DialogButtonBarDefaults] 次级按键表面色、胶囊圆角与 COUI 17 按钮材质。
 */
@Composable
internal fun CouiDialogSecondaryButton(
    text: String,
    contentColor: Color,
    hasMaterial: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (hasMaterial) {
        DialogButtonBarDefaults.materialButtonColors(textColor = contentColor)
    } else {
        DialogButtonBarDefaults.buttonColors(textColor = contentColor)
    }
    val interactionSource = remember { MutableInteractionSource() }
    CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = true)

    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        minWidth = 0.dp,
        minHeight = 44.dp,
        colors = colors,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        textStyle = COUITheme.textStyles.buttonM,
        material = ButtonMaterial.Secondary,
        materialBackdrop = false,
        interactionSource = interactionSource,
    )
}

/**
 * COUI 胶囊水平进度条：确定进度平滑补间填充，[progress] 为 null 时显示原生不定态动画。
 */
@Composable
internal fun CouiLinearProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val animatedProgress = if (progress != null) {
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = ActionProgressAnimMs),
            label = "coui-linear-progress",
        )
        animated
    } else {
        null
    }
    LinearProgressIndicator(
        modifier = modifier,
        progress = animatedProgress,
        height = height,
    )
}

/**
 * 悬浮玻璃交互外壳：复用 [LocalMaterialBackdrop] 实时背景模糊、焦点描边、按压回弹与 COUI 触觉。
 * 由 [CouiInstallProgressButton] 与 [CouiCircleActionButton] 共享。
 */
@Composable
internal fun CouiFloatingInteractiveBox(
    shape: Shape,
    backgroundColor: Color,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdropSpec: MaterialBackdropSpec? = null,
    shadow: Shadow? = null,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    content: @Composable BoxScope.(dark: Boolean) -> Unit,
) {
    val dark = COUITheme.isDark
    val interactionSource = remember { MutableInteractionSource() }
    if (interactive) {
        CouiSwitchHapticEffect(interactionSource = interactionSource, enabled = true)
    }
    val backdropProvider = if (backdropSpec != null && isRuntimeShaderSupported()) {
        LocalMaterialBackdrop.current
    } else {
        null
    }
    val backdropModifier = if (backdropProvider != null && backdropSpec != null) {
        remember(backdropProvider, backdropSpec, shape) {
            backdropProvider.materialBackdrop(backdropSpec, shape)
        }
    } else {
        Modifier
    }
    val resolvedFill = if (backdropProvider != null && backdropSpec != null) Color.Transparent else backgroundColor

    Box(
        modifier = modifier
            .then(if (interactive) Modifier.indication(interactionSource, SinkFeedback()) else Modifier)
            .then(if (shadow != null) Modifier.dropShadow(shape = shape, shadow = shadow) else Modifier)
            .couiFocusStroke(interactionSource = interactionSource, shape = shape, enabled = interactive)
            .clip(shape)
            .background(resolvedFill)
            .then(backdropModifier)
            .then(
                if (borderWidth > 0.dp && borderColor.alpha > 0f) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                },
            )
            .then(
                if (interactive) {
                    Modifier
                        .couiTouchFeedback(interactionSource = interactionSource, enabled = true)
                        .indication(interactionSource, LocalIndication.current)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(dark)
    }
}

/**
 * 进度按钮共享前景内容：下载中绘制确定态双遍裁剪反白或不定态流光，常规态居中展示标签。
 */
@Composable
private fun BoxScope.CouiInstallProgressLabelContent(
    text: String,
    enabled: Boolean,
    progress: Float?,
    isProgressActive: Boolean,
    floating: Boolean,
    dark: Boolean,
    scheme: Colors,
) {
    if (isProgressActive) {
        if (progress != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = ActionProgressAnimMs),
                label = "coui-install-progress",
            )
            val restLabelColor = when {
                floating && dark -> Color.White.copy(alpha = 0.92f)
                else -> scheme.primaryText
            }
            val fillColor = if (floating) scheme.primary.copy(alpha = if (dark) 0.88f else 0.90f) else scheme.primary
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = COUITheme.textStyles.buttonL,
                    color = restLabelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val fillWidth = size.width * animatedProgress
                        val isRtl = layoutDirection == LayoutDirection.Rtl
                        clipRect(
                            left = if (isRtl) size.width - fillWidth else 0f,
                            right = if (isRtl) size.width else fillWidth,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .background(fillColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = COUITheme.textStyles.buttonL,
                    color = scheme.onPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            val sweepWidth = 0.42f
            val transition = rememberInfiniteTransition(label = "coui-install-indeterminate")
            val sweep by transition.animateFloat(
                initialValue = -sweepWidth,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = ActionIndeterminateAnimMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "coui-install-sweep",
            )
            val sweepColor = scheme.primary.copy(alpha = if (dark) 0.40f else 0.26f)
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    color = sweepColor,
                    topLeft = Offset(size.width * sweep, 0f),
                    size = Size(size.width * sweepWidth, size.height),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = COUITheme.textStyles.buttonL,
                    color = scheme.labelPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    val labelColor = if (enabled) scheme.onPrimary else scheme.labelSecondary
    Text(
        text = text,
        style = COUITheme.textStyles.buttonL,
        color = labelColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * 胶囊进度主按钮（suqi8/coui）：
 * - 外层提供 [LocalMaterialBackdrop] 且支持运行时着色器时启用实时背景模糊；
 * - [isProgressActive] 为 true 且 [progress] 非空时采用双遍裁剪文字绘制（底层浅轨配高对比文字，
 *   进度区用 [clipRect] 裁出主题色实底与纯白文字）；
 * - [isProgressActive] 为 true 且 [progress] 为 null 时展示不定态流光。
 */
@Composable
internal fun CouiInstallProgressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: Float? = null,
    isProgressActive: Boolean = false,
    @Suppress("UNUSED_PARAMETER") backdrop: CouiBackdrop? = null,
) {
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    val interactive = enabled && !isProgressActive
    val progressSemantics = if (isProgressActive) {
        val clamped = progress?.coerceIn(0f, 1f)
        Modifier.semantics {
            contentDescription = text
            progressBarRangeInfo = if (clamped != null) {
                ProgressBarRangeInfo(clamped, 0f..1f)
            } else {
                ProgressBarRangeInfo.Indeterminate
            }
        }
    } else {
        Modifier
    }

    val capsuleShape = remember { SquircleShape(COUIRoundCorner.Full, COUIRoundCorner.CapsuleWeight) }
    val hasBackdrop = LocalMaterialBackdrop.current != null && isRuntimeShaderSupported()
    val backdropSpec = remember(interactive, dark, scheme.primary) {
        if (interactive) {
            MaterialBackdropSpec(
                blurRadiusPx = 10f,
                layer1 = scheme.primary.copy(alpha = (if (dark) 230f else 229f) / 255f),
                mode1 = BlendMode.SrcOver,
                layer2 = Color(0x19FFFFFF),
                mode2 = BlendMode.Overlay,
            )
        } else {
            MaterialBackdropSpec(
                blurRadiusPx = 25f,
                layer1 = if (dark) Color(0x33FFFFFF) else Color(0xCCFFFFFF),
                mode1 = BlendMode.SrcOver,
                layer2 = Color.Transparent,
                mode2 = BlendMode.SrcOver,
            )
        }
    }
    val fallbackBackground = when {
        isProgressActive -> scheme.primary.copy(alpha = if (dark) 0.22f else 0.14f)
        enabled -> scheme.primary
        else -> scheme.labelPrimary.copy(alpha = if (dark) 0.10f else 0.08f)
    }
    val shadow = remember(interactive, scheme.primary) {
        if (interactive) {
            Shadow(
                radius = 12.dp,
                color = scheme.primary,
                offset = DpOffset(0.dp, 3.dp),
                alpha = 0.28f,
            )
        } else {
            null
        }
    }

    CouiFloatingInteractiveBox(
        shape = capsuleShape,
        backgroundColor = fallbackBackground,
        interactive = interactive,
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .then(progressSemantics),
        backdropSpec = backdropSpec,
        shadow = shadow,
    ) { resolvedDark ->
        CouiInstallProgressLabelContent(
            text = text,
            enabled = enabled,
            progress = progress,
            isProgressActive = isProgressActive,
            floating = hasBackdrop,
            dark = resolvedDark,
            scheme = scheme,
        )
    }
}

/**
 * 圆形次级动作按钮：与底栏主进度胶囊并排提供取消下载/重新下载等快捷操作；
 * 外层提供 [LocalMaterialBackdrop] 时自动呈现毛玻璃材质。
 */
@Composable
internal fun CouiCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") backdrop: CouiBackdrop? = null,
) {
    val dark = COUITheme.isDark
    val scheme = COUITheme.colorScheme
    val resolvedTint = tint.takeOrElse { scheme.labelPrimary }
    val borderColor = scheme.labelPrimary.copy(alpha = if (dark) 0.14f else 0.08f)
    val backdropSpec = remember(dark) {
        MaterialBackdropSpec(
            blurRadiusPx = 25f,
            layer1 = if (dark) Color(0x80666666) else Color(0xB3666666),
            mode1 = if (dark) BlendMode.Overlay else BlendMode.ColorDodge,
            layer2 = if (dark) Color(0xB3262626) else Color(0x99F5F5F5),
            mode2 = BlendMode.Luminosity,
        )
    }
    val shadow = remember {
        Shadow(
            radius = 10.dp,
            color = Color.Black,
            offset = DpOffset(0.dp, 2.dp),
            alpha = 45f / 255f,
        )
    }

    CouiFloatingInteractiveBox(
        shape = CircleShape,
        backgroundColor = scheme.cardBackground,
        interactive = true,
        onClick = onClick,
        modifier = modifier.size(48.dp),
        backdropSpec = backdropSpec,
        shadow = shadow,
        borderWidth = 0.5.dp,
        borderColor = borderColor,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = resolvedTint,
        )
    }
}
