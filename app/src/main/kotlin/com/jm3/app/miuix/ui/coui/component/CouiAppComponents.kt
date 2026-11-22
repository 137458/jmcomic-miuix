// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
