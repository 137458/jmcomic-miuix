// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.layout.Scaffold as LegacyCouiScaffold
import com.itos.coui.layout.rememberCouiImeInsets
import com.itos.coui.theme.CouiTheme as LegacyCouiTheme
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarColors
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.alignToReadableContent
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import io.github.suqi8.coui.kmp.basic.FabPosition
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.ToolbarPosition
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.COUIIndication
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.couiTouchFeedback
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

/**
 * suqi8/coui 页面骨架：装配顶栏、底栏净空、FAB、浮层工具栏与页面材质模糊录制源。
 *
 * - 通过 [rememberPageBackdrop] + [Modifier.layerBackdrop] 录制正文区，
 *   并向顶栏与弹窗/菜单/底栏/FAB 提供 [LocalPageBackdrop]、[LocalMaterialBackdrop]、[LocalPopupMaterialBlur]，
 *   避免浮层与按钮自采样。
 * - 底栏净空通过 [LocalFloatingBarContentPadding] 在 bottomBar 槽位留出等高占位，
 *   让正文的 innerPadding 与 FAB 一起抬到底栏之上。
 * - IME 避让受 [rememberCouiImeOccupiesPage] 窗口焦点门控保护，输入弹窗持焦点时不提前收缩宿主。
 */
@Composable
fun CouiPageScaffold(
    topBar: @Composable (backdrop: LayerBackdrop?) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    imePadding: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val barClearance = LocalFloatingBarContentPadding.current
    val backdrop = rememberPageBackdrop()
    val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
    val popupBlur = rememberPopupMaterialBlur(backdrop)
    val imeOccupiesPage = imePadding && rememberCouiImeOccupiesPage()
    val imeInsets = if (imeOccupiesPage) {
        WindowInsets.ime.only(WindowInsetsSides.Bottom)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    Scaffold(
        modifier = modifier.windowInsetsPadding(imeInsets),
        topBar = { topBar(backdrop) },
        bottomBar = {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalMaterialBackdrop provides materialBackdrop,
            ) {
                if (bottomBar != null) {
                    bottomBar()
                } else if (barClearance > 0.dp) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(barClearance)
                            .navigationBarsPadding(),
                    )
                }
            }
        },
        floatingActionButton = floatingActionButton?.let { fab ->
            {
                CompositionLocalProvider(
                    LocalPageBackdrop provides backdrop,
                    LocalMaterialBackdrop provides materialBackdrop,
                ) {
                    Box(modifier = Modifier.alignToReadableContent()) {
                        fab()
                    }
                }
            }
        } ?: {},
        floatingActionButtonPosition = floatingActionButtonPosition,
        floatingToolbar = {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalMaterialBackdrop provides materialBackdrop,
            ) {
                floatingToolbar()
            }
        },
        floatingToolbarPosition = floatingToolbarPosition,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalPopupMaterialBlur provides popupBlur,
            ) {
                content(innerPadding)
            }
        }
    }
}

/**
 * 过渡期旧版页面采样源：供未迁移页面读取 [CouiBackdrop]，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
val LocalCouiAppBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

/**
 * 过渡期旧版页面骨架：供未迁移页面继续使用 `com.itos.coui` 顶栏与采样源，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
@Suppress("DEPRECATION")
@Composable
fun CouiAppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable BoxScope.() -> Unit)? = null,
    contentBackdrop: CouiBackdrop? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val barClearance = LocalFloatingBarContentPadding.current
    LegacyCouiScaffold(
        modifier = modifier.windowInsetsPadding(rememberCouiImeInsets()),
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
        floatingActionButton = floatingActionButton?.let { fab ->
            {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alignToReadableContent(),
                ) { fab() }
            }
        },
        content = { innerPadding ->
            if (contentBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .couiBackdrop(contentBackdrop)
                        .background(LegacyCouiTheme.colorScheme.background),
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

internal val CouiDetailButtonCornerRadius = 16.dp

/** 全 COUI 评论、回复与筛选输入区的统一圆角轮廓：比卡片更方，输入区不随皮肤随意变形。 */
val CouiCommentFieldShape: Shape = SquircleShape(cornerRadius = CouiDetailButtonCornerRadius, weight = 3f)

/** 详情页操作按钮的统一圆角轮廓：主阅读按钮、功能瓦片与菜单行共用。 */
internal val CouiDetailButtonShape: Shape = CouiCommentFieldShape

/** 详情页主阅读大按钮的默认轮廓。 */
internal val CouiPrimaryActionDefaultShape: Shape = CouiDetailButtonShape

/**
 * 按压反馈 + 无涟漪点击：COUI 页面里所有自绘可点区域的统一入口（suqi8/coui 2.1.0）。
 *
 * 反馈分三层：[COUIIndication] + [couiTouchFeedback] 提供悬停/聚焦/按下蒙层，
 * [scaleOnPress] 打开时在视觉层施加弹簧按压缩放，按下瞬间由 [rememberCouiHaptic] 发一次
 * [CouiHapticEffect.Switch] 触觉。触区补足放在视觉层外侧，避免把可见胶囊撑大。
 */
@Composable
fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape? = null,
    backgroundColor: Color = Color.Unspecified,
    minTouchTarget: Dp = 0.dp,
    scaleOnPress: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = rememberCouiHaptic()
    // 每次按住只发一次：滑动、长按都不会再触发，避免连续震动
    LaunchedEffect(interactionSource, haptic) {
        var down = false
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                if (!down) {
                    down = true
                    haptic(CouiHapticEffect.Switch)
                }
            } else if (interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) {
                down = false
            }
        }
    }

    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = remember { Animatable(1f) }
    LaunchedEffect(scaleOnPress, pressed) {
        if (!scaleOnPress) {
            if (pressScale.value != 1f) pressScale.snapTo(1f)
        } else {
            val target = if (pressed) 0.95f else 1f
            val spec = if (pressed) {
                spring<Float>(dampingRatio = 1f, stiffness = 631.65f)
            } else {
                spring<Float>(dampingRatio = 0.5f, stiffness = 157.91f)
            }
            pressScale.animateTo(target, spec)
        }
    }

    val colors = COUITheme.colorScheme
    val indication = remember(colors.labelPrimary, colors.press, colors.hover, colors.focus) {
        COUIIndication(
            color = colors.labelPrimary,
            pressColor = colors.press,
            hoverColor = colors.hover,
            focusColor = colors.focus,
        )
    }

    val visualSurface = Modifier
        .then(
            if (scaleOnPress) {
                Modifier.graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
            } else {
                Modifier
            },
        )
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .then(if (backgroundColor != Color.Unspecified) Modifier.background(backgroundColor) else Modifier)
        .indication(interactionSource = interactionSource, indication = indication)

    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        .couiTouchFeedback(interactionSource = interactionSource, enabled = enabled)
        .couiMinimumTouchTarget(minTouchTarget)
        .then(visualSurface)
}

/** 过渡期重载：兼容显式传入旧版 [SpotlightPreset] 的未迁移调用方，Wave 3 移除。 */
@Deprecated("Wave 3 removal")
@Composable
fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape? = null,
    backgroundColor: Color = Color.Unspecified,
    minTouchTarget: Dp = 0.dp,
    scaleOnPress: Boolean = false,
    @Suppress("UNUSED_PARAMETER") spotlight: SpotlightPreset?,
    @Suppress("UNUSED_PARAMETER") spotlightAboveContent: Boolean = false,
    onClick: () -> Unit,
): Modifier = couiPressable(
    enabled = enabled,
    role = role,
    shape = shape,
    backgroundColor = backgroundColor,
    minTouchTarget = minTouchTarget,
    scaleOnPress = scaleOnPress,
    onClick = onClick,
)

/** 过渡期重载：兼容仅显式传入 `spotlightAboveContent` 的未迁移调用方，Wave 3 移除。 */
@Deprecated("Wave 3 removal")
@Composable
fun Modifier.couiPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape? = null,
    backgroundColor: Color = Color.Unspecified,
    minTouchTarget: Dp = 0.dp,
    scaleOnPress: Boolean = false,
    @Suppress("UNUSED_PARAMETER") spotlightAboveContent: Boolean,
    onClick: () -> Unit,
): Modifier = couiPressable(
    enabled = enabled,
    role = role,
    shape = shape,
    backgroundColor = backgroundColor,
    minTouchTarget = minTouchTarget,
    scaleOnPress = scaleOnPress,
    onClick = onClick,
)

/**
 * 过渡期凝光叠层：供未迁移的 HUD/导航/动作组件使用，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
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
        dark = COUITheme.isDark,
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
 * 过渡期旧版顶栏标题槽：读取 `com.itos.coui` 顶栏下发的 [LocalCouiTextStyle]，Wave 3 移除。
 * 新迁移页面请直接向 [CouiPageScaffold] / `TopAppBar` 传入 `title: String` 或使用 [CouiPageTopBarTitle]。
 */
@Deprecated("Wave 3 removal")
@Composable
fun CouiTopBarTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val style = couiScaledStyle(LocalCouiTextStyle.current)
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style else style.copy(textAlign = textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 过渡期旧版顶栏底色：供未迁移页面的 `com.itos.coui` `LargeTopAppBar` 使用，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
@Composable
fun couiTopAppBarColors(): CouiTopAppBarColors = CouiTopAppBarDefaults.colors(
    backgroundColor = COUITheme.colorScheme.backgroundWithCard,
    dark = COUITheme.isDark,
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

internal const val CouiProgressAnimMs = 250
internal const val CouiIndeterminateAnimMs = 1200

/**
 * 悬浮控件是否启用 COUI 实时凝光材质：要求 API 34+ 硬件渲染链路且宿主已提供有效 backdrop。
 */
internal fun couiFloatingSurfaceEnabled(sdkInt: Int, hasBackdrop: Boolean): Boolean =
    shouldUseCouiBlur(
        blurEnabled = true,
        hasBackdrop = hasBackdrop,
        runtimeShaderSupported = sdkInt >= CouiLibraryNavBarMinSdk,
    )

/**
 * 轮廓柔光材质是否启用：要求 API 33+ RuntimeShader 支持。
 */
internal fun couiContourSurfaceEnabled(sdkInt: Int): Boolean =
    shouldUsePageBackdrop(
        blurEnabled = true,
        runtimeShaderSupported = sdkInt >= Build.VERSION_CODES.TIRAMISU,
    )

/**
 * 过渡期 COUI 原厂凝光轮廓材质修饰符：供尚未迁移的弹窗与更新页调用，Wave 3 移除。
 */
@Deprecated("Wave 3 removal")
@Composable
internal fun Modifier.couiContourSurface(
    shape: Shape,
    surfaceColor: Color,
    cornerRadius: Dp,
    cornerWeight: Float = 2f,
    style: CouiContourStyle = CouiContourStyle.Button,
    dark: Boolean = COUITheme.isDark,
    emphasized: Boolean = false,
    edgeAlpha: Float = when {
        emphasized -> if (dark) 0.24f else 0.16f
        dark -> 0.14f
        else -> 0.05f
    },
    innerGlowAlpha: Float = when {
        emphasized -> if (dark) 0.10f else 0.06f
        dark -> 0.04f
        else -> 0.02f
    },
    edgeWidthDp: Float = if (emphasized) 2.5f else 2.2f,
    fallbackBorderColor: Color = Color.Transparent,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !couiContourSurfaceEnabled(Build.VERSION.SDK_INT)) {
        return this
            .clip(shape)
            .background(surfaceColor)
            .then(
                if (fallbackBorderColor.alpha > 0f) {
                    Modifier.border(0.5.dp, fallbackBorderColor, shape)
                } else {
                    Modifier
                },
            )
    }

    return couiContourSurface33(
        shape = shape,
        surfaceColor = surfaceColor,
        cornerRadius = cornerRadius,
        cornerWeight = cornerWeight,
        style = style,
        edgeAlpha = edgeAlpha,
        innerGlowAlpha = innerGlowAlpha,
        edgeWidthDp = edgeWidthDp,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.couiContourSurface33(
    shape: Shape,
    surfaceColor: Color,
    cornerRadius: Dp,
    cornerWeight: Float,
    style: CouiContourStyle,
    edgeAlpha: Float,
    innerGlowAlpha: Float,
    edgeWidthDp: Float,
): Modifier {
    val contourGlow = LocalCouiContourGlowEnabled.current
    val contour = remember(contourGlow, style) {
        if (contourGlow) CouiContour(floating = false, style = style) else null
    }
    val shaderPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val density = LocalDensity.current.density
    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }

    return this
        .clip(shape)
        .drawWithContent {
            drawRect(surfaceColor)
            if (contour != null && size.width > 0f && size.height > 0f) {
                val resolvedRadius = minOf(cornerRadiusPx, size.minDimension / 2f)
                val resolvedWeight = if (style == CouiContourStyle.Button && size.width == size.height) {
                    2f
                } else {
                    cornerWeight
                }
                shaderPaint.shader = contour.shader(
                    size = size,
                    density = density,
                    radiusPx = resolvedRadius,
                    cornerWeight = resolvedWeight,
                    surfaceColor = Color.Transparent,
                    innerGlowAlpha = innerGlowAlpha,
                    lineAlpha = edgeAlpha,
                    effectAlpha = 1f,
                    lineWidthPx = edgeWidthDp * density,
                )
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, shaderPaint)
            }
            drawContent()
        }
}

/**
 * 弹层内长列表的最大高度：手机断点以下保持既有设计值，大屏放宽到窗口可用高度。
 */
@Composable
fun couiDialogListMaxHeight(compactMaxHeight: Dp): Dp {
    val wide = LocalWideLayout.current
    if (wide.contentMaxWidth == null) return compactMaxHeight
    val wideMax = wide.dialogMaxHeight ?: return compactMaxHeight
    return wideMax
        .coerceAtMost(DialogDefaults.MaxHeight)
        .coerceAtLeast(compactMaxHeight)
}
