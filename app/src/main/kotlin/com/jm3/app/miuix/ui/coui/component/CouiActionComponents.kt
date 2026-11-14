// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon

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
    shape: androidx.compose.ui.graphics.Shape = CouiDetailButtonShape,
    onClick: () -> Unit,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val bgColor = if (enabled) colors.accent else colors.container
    val contentColor = if (enabled) colors.onAccent else colors.onSurfaceVariant
    val titleStyle = rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
        color = contentColor,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .couiPressable(
                shape = shape,
                backgroundColor = bgColor,
                spotlight = if (enabled) SpotlightPreset.OpaqueSmall1 else null,
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
            BasicText(
                text = title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    kind = CouiTextKind.ItemSummary,
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
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorSchemeFor(dark)
    val bgColor = when {
        active && dark -> lerp(colors.container, colors.accent, 0.24f)
        active -> lerp(colors.card, colors.accent, 0.14f)
        dark -> lerp(colors.container, Color.White, 0.06f)
        else -> colors.background
    }
    val fgColor = when {
        active -> colors.accent
        enabled || onClick == null -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val borderColor = when {
        active -> colors.accent.copy(alpha = if (dark) 0.34f else 0.22f)
        dark -> colors.onSurface.copy(alpha = 0.12f)
        else -> colors.onSurface.copy(alpha = 0.06f)
    }
    val baseModifier = modifier
        .heightIn(min = 56.dp)
        .let { m ->
            if (onClick != null) {
                m.couiPressable(
                    shape = CouiDetailButtonShape,
                    backgroundColor = bgColor,
                    spotlight = if (enabled) SpotlightPreset.ChipTab else null,
                    enabled = enabled,
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = onClick,
                )
            } else {
                m
                    .clip(CouiDetailButtonShape)
                    .background(bgColor)
            }
        }
        .border(0.5.dp, borderColor, CouiDetailButtonShape)
        .padding(horizontal = 6.dp, vertical = 9.dp)
    Column(
        modifier = baseModifier,
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
            kind = CouiTextKind.ItemSummary,
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
 * 保持紧致高度而不把 48dp minTouchTarget 撑进标题行内，全站所有分区标题角标风格一致。
 */
@Composable
fun CouiHeaderCapsuleAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    tinted: Boolean = true,
    backdrop: CouiBackdrop? = null,
) {
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorSchemeFor(dark)
    val bgColor = when {
        tinted -> colors.accent.copy(alpha = if (dark) 0.18f else 0.12f)
        dark -> colors.onSurface.copy(alpha = 0.09f)
        else -> colors.onSurface.copy(alpha = 0.06f)
    }
    val borderColor = when {
        tinted -> colors.accent.copy(alpha = if (dark) 0.34f else 0.20f)
        dark -> colors.onSurface.copy(alpha = 0.14f)
        else -> colors.onSurface.copy(alpha = 0.08f)
    }
    val fgColor = if (tinted) colors.accent else colors.onSurfaceVariant

    val renderer = remember(backdrop != null) {
        if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk &&
            couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null)
        ) {
            runCatching { CouiFloatingRenderer.standalone() }.getOrNull()
        } else {
            null
        }
    }
    if (couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null) && backdrop != null && renderer != null) {
        if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
            val density = LocalDensity.current
            val contourGlow = LocalCouiContourGlowEnabled.current
            val extensionPx = with(density) { 4.dp.roundToPx() }
            val material = remember(dark, extensionPx) {
                CouiTopAppBarButtonDefaults.systemMaterial(dark = dark, capsule = true).copy(
                    blurRadius = 25f,
                    extension = extensionPx,
                )
            }
            val shadow = remember(dark, density.density, contourGlow) {
                CouiTopAppBarButtonDefaults.shadow(
                    dark = dark,
                    capsule = true,
                    density = density.density,
                    contour = contourGlow,
                )
            }
            CouiFloatingInteractiveBox(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = CouiCapsuleShape,
                cornerWeight = 2f,
                spotlightPreset = SpotlightPreset.ChipTab,
                interactive = true,
                onClick = onClick,
                modifier = modifier.heightIn(min = 28.dp),
                matchParentSizeContent = false,
            ) {
                CouiHeaderCapsuleActionContent(
                    text = text,
                    trailingIcon = trailingIcon,
                    fgColor = fgColor,
                )
            }
            return
        }
    }

    Row(
        modifier = modifier
            .heightIn(min = 28.dp)
            .couiPressable(
                shape = CouiCapsuleShape,
                spotlight = SpotlightPreset.ChipTab,
                scaleOnPress = true,
                onClick = onClick,
            )
            .couiContourSurface(
                shape = CouiCapsuleShape,
                surfaceColor = bgColor,
                cornerRadius = 999.dp,
                cornerWeight = 2f,
                dark = dark,
                emphasized = tinted,
                fallbackBorderColor = borderColor,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CouiHeaderCapsuleActionLabel(
            text = text,
            trailingIcon = trailingIcon,
            fgColor = fgColor,
        )
    }
}

@Composable
private fun CouiHeaderCapsuleActionContent(
    text: String,
    trailingIcon: ImageVector?,
    fgColor: Color,
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CouiHeaderCapsuleActionLabel(
            text = text,
            trailingIcon = trailingIcon,
            fgColor = fgColor,
        )
    }
}

@Composable
private fun CouiHeaderCapsuleActionLabel(
    text: String,
    trailingIcon: ImageVector?,
    fgColor: Color,
) {
    Text(
        text = text,
        kind = CouiTextKind.GroupFooter,
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
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorSchemeFor(dark)
    val bgColor = when {
        highlighted -> colors.accent.copy(alpha = if (dark) 0.18f else 0.11f)
        glassSurface -> colors.onSurface.copy(alpha = if (dark) 0.10f else 0.06f)
        dark -> colors.onSurface.copy(alpha = 0.09f)
        else -> lerp(colors.background, colors.card, 0.28f)
    }
    val borderColor = when {
        highlighted -> colors.accent.copy(alpha = if (dark) 0.34f else 0.22f)
        dark -> colors.onSurface.copy(alpha = 0.14f)
        else -> colors.onSurface.copy(alpha = 0.08f)
    }
    val textColor = if (highlighted) colors.accent else colors.onSurface
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 30.dp else 34.dp)
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = bgColor,
                spotlight = SpotlightPreset.ChipTab,
                scaleOnPress = true,
                onClick = onClick,
            )
            .border(0.5.dp, borderColor, CouiCapsuleShape)
            .padding(
                horizontal = if (compact) 11.dp else 14.dp,
                vertical = if (compact) 5.dp else 7.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = if (compact) CouiTextKind.GroupFooter else CouiTextKind.ItemSummary,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 整行开关：库 Switch 传 null 回调后只呈现状态、不再自带 toggleable，
 * 点击、开关角色与开/关播报由这里统一承担；反馈层与 toggleable 共用同一个
 * interactionSource（库的用法约定），按压蒙层与普通设置行一致。
 *
 * 凝光补在蒙层之后：[Item] 的行底色由外层 [Group] 铺，行本体不画底色，
 * 因此这里叠光不会被行底色盖住；关闭的开关不接光（[interactive] 跟随 enabled）。
 */
@Composable
internal fun Modifier.couiSwitchRow(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .couiButtonFeedback(
            dark = CouiTheme.isDark,
            interactionSource = interactionSource,
            enabled = enabled,
        )
        .couiSpotlightLight(SpotlightPreset.OpaqueSmall2, interactive = enabled)
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
 * 与 COUI 原生 `DialogButton(Secondary)` 等价的弹窗辅助按钮：
 * 沿用 COUI 次级按键表面色、胶囊圆角、`CouiDialogDefaults.buttonHeight` 与 [CouiContour] 凝光轮廓材质。
 */
@Composable
internal fun CouiDialogSecondaryButton(
    text: String,
    contentColor: Color,
    hasMaterial: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorSchemeFor(dark)
    val surfaceColor = when {
        hasMaterial && dark -> Color(0x33FFFFFF)
        hasMaterial -> Color(0xA6FFFFFF)
        dark -> Color(0xFF353535)
        else -> Color(0xFFEDEDEB)
    }
    Box(
        modifier = modifier
            .heightIn(min = CouiDialogDefaults.buttonHeight)
            .couiPressable(
                shape = CouiCapsuleShape,
                spotlight = SpotlightPreset.ChipTab,
                scaleOnPress = true,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .couiContourSurface(
                shape = CouiCapsuleShape,
                surfaceColor = surfaceColor,
                cornerRadius = 999.dp,
                cornerWeight = 2f,
                style = CouiContourStyle.Button,
                dark = dark,
                emphasized = false,
                innerGlowAlpha = if (dark) 0.01f else 0.05f,
                fallbackBorderColor = colors.onSurface.copy(alpha = if (dark) 0.14f else 0.08f),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemValue,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * COUI 胶囊水平进度条：确定进度平滑补间填充，[progress] 为 null 时显示连续滑移的不定态光带。
 */
@Composable
internal fun CouiLinearProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val dark = CouiTheme.isDark
    val scheme = CouiTheme.colorSchemeFor(dark)
    val trackColor = scheme.accent.copy(alpha = if (dark) 0.22f else 0.14f)
    if (progress != null) {
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = CouiProgressAnimMs),
            label = "coui-linear-progress",
        )
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(CouiCapsuleShape)
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CouiCapsuleShape)
                    .background(scheme.accent),
            )
        }
    } else {
        val sweepWidth = 0.36f
        val transition = rememberInfiniteTransition(label = "coui-linear-indeterminate")
        val sweep by transition.animateFloat(
            initialValue = -sweepWidth,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = CouiIndeterminateAnimMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "coui-linear-sweep",
        )
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(CouiCapsuleShape)
                .background(trackColor),
        ) {
            val left = size.width * sweep
            val width = size.width * sweepWidth
            drawRect(
                color = scheme.accent,
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
            )
        }
    }
}

/**
 * 悬浮玻璃交互外壳：复用 COUI `couiFloatingSurface` + 触控形变 + 糖果按压弹簧 + 跟手凝光 + 触觉。
 * 由 [CouiInstallProgressButton] 与 [CouiCircleActionButton] 共享，保持 API 34+ 凝光材质一致。
 */
@RequiresApi(CouiLibraryNavBarMinSdk)
@Composable
internal fun CouiFloatingInteractiveBox(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    material: CouiFloatingMaterial,
    shadow: CouiFloatingShadow,
    shape: Shape,
    cornerWeight: Float,
    spotlightPreset: SpotlightPreset,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    matchParentSizeContent: Boolean = true,
    content: @Composable BoxScope.(dark: Boolean) -> Unit,
) {
    val dark = CouiTheme.isDark
    val contourGlow = LocalCouiContourGlowEnabled.current
    val contentLayer = rememberGraphicsLayer()
    val motion = rememberCouiTouchMotion(interactive)
    val press = rememberCouiPressMotion()
    val interactionSource = remember { MutableInteractionSource() }
    if (interactive) {
        CouiPressHapticEffect(interactionSource)
    }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = spotlightPreset,
        dark = dark,
    )
    var boundsSize by remember { mutableStateOf(IntSize.Zero) }
    val scaleXValue = if (interactive) motion.scaleX.value + press.scale.value - 1f else 1f
    val scaleYValue = if (interactive) motion.scaleY.value + press.scale.value - 1f else 1f

    Box(
        modifier = modifier
            .onSizeChanged { boundsSize = it }
            .couiTouchMotion(motion, interactive)
            .couiPressMotion(press, interactive)
            .then(lightInput)
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = shape,
                contentLayer = contentLayer,
                contourGlowEnabled = contourGlow,
                foregroundComposition = true,
                beforeContent = { lightClip ->
                    if (drawLight != null) {
                        withTransform({
                            translate(motion.translationX.value, motion.translationY.value)
                            scale(scaleXValue, scaleYValue, center)
                        }) {
                            clipPath(lightClip) { drawLight.invoke(this) }
                        }
                    }
                },
            ) {
                CouiFloatingSurface(
                    bounds = Rect(
                        Offset(motion.translationX.value, motion.translationY.value),
                        Size(boundsSize.width.toFloat(), boundsSize.height.toFloat()),
                    ),
                    scaleX = scaleXValue,
                    scaleY = scaleYValue,
                    cornerWeight = cornerWeight,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (matchParentSizeContent) Modifier.matchParentSize() else Modifier)
                .graphicsLayer {
                    translationX = motion.translationX.value
                    translationY = motion.translationY.value
                    scaleX = scaleXValue
                    scaleY = scaleYValue
                }
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(shape)
                .then(if (interactive) Modifier.couiFabFeedback(dark, interactionSource) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            content(dark)
        }
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
    scheme: CouiColorScheme,
) {
    if (isProgressActive) {
        if (progress != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = CouiProgressAnimMs),
                label = "coui-install-progress",
            )
            val restLabelColor = when {
                floating && dark -> Color.White.copy(alpha = 0.92f)
                dark -> scheme.accentOnSurface
                else -> scheme.accent
            }
            val fillColor = if (floating) scheme.accent.copy(alpha = if (dark) 0.88f else 0.90f) else scheme.accent
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    kind = CouiTextKind.ItemTitle,
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
                    kind = CouiTextKind.ItemTitle,
                    color = scheme.onAccent,
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
                    animation = tween(durationMillis = CouiIndeterminateAnimMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "coui-install-sweep",
            )
            val sweepColor = scheme.accent.copy(alpha = if (dark) 0.40f else 0.26f)
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
                    kind = CouiTextKind.ItemTitle,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    val labelColor = when {
        enabled -> scheme.onAccent
        floating -> scheme.onSurfaceVariant
        else -> scheme.onSurfaceVariant
    }
    Text(
        text = text,
        kind = CouiTextKind.ItemTitle,
        color = labelColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * 对齐 ColorOS 原厂 `COUIInstallLoadProgress` 与悬浮凝光材质的胶囊进度主按钮：
 * - 传入 [backdrop] 且系统 >= API 34 时启用 `couiFloatingSurface`（模糊折射、凝光轮廓边、焦散投影、触控形变与 `OpaqueMedium1` 跟手光）；
 * - [isProgressActive] 为 true 且 [progress] 非空时，采用双遍裁剪文字绘制（底层浅轨/玻璃配高对比文字，进度区用 [clipRect] 裁出实底与纯白文字）；
 * - [isProgressActive] 为 true 且 [progress] 为 null 时展示不定态流光；低版本退回胶囊实底 + [couiPressable]。
 */
@Composable
internal fun CouiInstallProgressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: Float? = null,
    isProgressActive: Boolean = false,
    backdrop: CouiBackdrop? = null,
) {
    val dark = CouiTheme.isDark
    val scheme = CouiTheme.colorSchemeFor(dark)
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

    val renderer = remember(backdrop != null) {
        if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk &&
            couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null)
        ) {
            runCatching { CouiFloatingRenderer.standalone() }.getOrNull()
        } else {
            null
        }
    }

    if (couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null) && backdrop != null && renderer != null) {
        if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
            val density = LocalDensity.current
            val contourGlow = LocalCouiContourGlowEnabled.current
            val extensionPx = with(density) { 4.dp.roundToPx() }
            val material = remember(isProgressActive, enabled, dark, scheme.accent, extensionPx) {
                if (interactive) {
                    CouiFabDefaults.material(color = scheme.accent, dark = dark).let { base ->
                        base.copy(
                            firstColor = base.firstColor.copy(alpha = if (dark) 0.84f else 0.88f),
                            blurRadius = 20f,
                            extension = extensionPx,
                        )
                    }
                } else {
                    CouiTopAppBarButtonDefaults.systemMaterial(dark = dark, capsule = true).copy(
                        blurRadius = 25f,
                        extension = extensionPx,
                    )
                }
            }
            val shadow = remember(interactive, dark, scheme.accent, density.density, contourGlow) {
                if (interactive) {
                    CouiFabDefaults.shadow(scheme.accent, density.density, contourGlow).copy(smoothCorner = true)
                } else {
                    CouiTopAppBarButtonDefaults.shadow(dark = dark, capsule = true, density = density.density, contour = contourGlow)
                }
            }
            CouiFloatingInteractiveBox(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = CouiCapsuleShape,
                cornerWeight = 1f,
                spotlightPreset = SpotlightPreset.OpaqueMedium1,
                interactive = interactive,
                onClick = onClick,
                modifier = modifier
                    .height(48.dp)
                    .then(progressSemantics),
            ) { resolvedDark ->
                CouiInstallProgressLabelContent(
                    text = text,
                    enabled = enabled,
                    progress = progress,
                    isProgressActive = isProgressActive,
                    floating = true,
                    dark = resolvedDark,
                    scheme = scheme,
                )
            }
            return
        }
    }

    val trackBg = when {
        isProgressActive -> scheme.accent.copy(alpha = if (dark) 0.22f else 0.14f)
        enabled -> scheme.accent
        else -> scheme.onSurface.copy(alpha = if (dark) 0.10f else 0.08f)
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .then(progressSemantics)
            .then(
                if (interactive) {
                    Modifier.couiPressable(
                        shape = CouiCapsuleShape,
                        spotlight = SpotlightPreset.OpaqueMedium1,
                        enabled = true,
                        scaleOnPress = true,
                        minTouchTarget = CouiMinTouchTarget,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .couiContourSurface(
                shape = CouiCapsuleShape,
                surfaceColor = trackBg,
                cornerRadius = 999.dp,
                cornerWeight = 2f,
                dark = dark,
                emphasized = interactive,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CouiInstallProgressLabelContent(
            text = text,
            enabled = enabled,
            progress = progress,
            isProgressActive = isProgressActive,
            floating = false,
            dark = dark,
            scheme = scheme,
        )
    }
}

/**
 * 圆形次级动作按钮：与底栏主进度胶囊并排提供取消下载/重新下载等快捷操作；
 * 传入 [backdrop] 且系统 >= API 34 时启用 COUI 原厂圆形悬浮玻璃凝光材质。
 */
@Composable
internal fun CouiCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    backdrop: CouiBackdrop? = null,
) {
    val dark = CouiTheme.isDark
    val scheme = CouiTheme.colorSchemeFor(dark)
    val resolvedTint = tint.takeOrElse { scheme.onSurface }
    val renderer = remember(backdrop != null) {
        if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk &&
            couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null)
        ) {
            runCatching { CouiFloatingRenderer.standalone() }.getOrNull()
        } else {
            null
        }
    }

    if (couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null) && backdrop != null && renderer != null) {
        if (Build.VERSION.SDK_INT >= CouiLibraryNavBarMinSdk) {
            val density = LocalDensity.current
            val contourGlow = LocalCouiContourGlowEnabled.current
            val extensionPx = with(density) { 4.dp.roundToPx() }
            val material = remember(dark, extensionPx) {
                CouiTopAppBarButtonDefaults.systemMaterial(dark = dark, capsule = false).copy(
                    blurRadius = 25f,
                    extension = extensionPx,
                )
            }
            val shadow = remember(dark, density.density, contourGlow) {
                CouiTopAppBarButtonDefaults.shadow(
                    dark = dark,
                    capsule = false,
                    density = density.density,
                    contour = contourGlow,
                )
            }
            CouiFloatingInteractiveBox(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = CouiTopAppBarButtonDefaults.circleShape,
                cornerWeight = 2f,
                spotlightPreset = SpotlightPreset.OpaqueSmall2,
                interactive = true,
                onClick = onClick,
                modifier = modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = resolvedTint,
                )
            }
            return
        }
    }

    val borderColor = scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.08f)
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                spotlight = SpotlightPreset.OpaqueSmall2,
                scaleOnPress = true,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(48.dp)
            .couiContourSurface(
                shape = CircleShape,
                surfaceColor = scheme.container,
                cornerRadius = 999.dp,
                cornerWeight = 2f,
                dark = dark,
                emphasized = false,
                fallbackBorderColor = borderColor,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = resolvedTint,
        )
    }
}
