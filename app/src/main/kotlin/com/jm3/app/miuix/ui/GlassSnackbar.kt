// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.LocalGlassBackdrop
import com.jm3.app.miuix.ui.glass.vibrancy
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SnackbarData
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊圆角：与 Miuix Card / Snackbar 的 16dp 同一族，取 22dp 让单行文案接近胶囊。 */
private val GlassSnackbarShape: Shape = RoundedCornerShape(22.dp)

/** 玻璃底衬不透明度：模糊把背景压平后仍需盖住高频细节，低于 0.6 会出现文字发灰。 */
private const val GLASS_SNACKBAR_SURFACE_ALPHA = 0.68f

/** 设备不支持 RuntimeShader 时的实色兜底不透明度。 */
private const val GLASS_SNACKBAR_OPAQUE_ALPHA = 0.95f

/** 模糊半径：提示条尺寸小，半径再大只会把背景糊成均匀色，反而失去玻璃层次。 */
private val GlassSnackbarBlurRadius = 20.dp

/** Miuix SnackbarHost 内部已留的底边距（HostBottomPadding，私有常量），补净空时需要扣掉。 */
private val SnackbarHostBottomPadding: Dp = 12.dp

/**
 * 玻璃提示条宿主：替代各页 Scaffold 的 `SnackbarHost(...)`。
 *
 * 与 Miuix [SnackbarHost] 的两点差异：
 * 1. 位置：[LocalFloatingBarContentPadding] 描述悬浮胶囊底栏占用的高度，宿主整体上抬同样的
 *    距离，提示条才不会被底栏压住；子页面没有底栏时该值为 0，提示条自然贴底。
 * 2. 材质：默认内容换成自绘的玻璃胶囊（[GlassSnackbar]）。不能直接包裹 Miuix `Snackbar`——
 *    它自己用 squircleBackground 画底，外面再叠模糊会被它的 OuterPadding 撑出一圈光晕。
 *
 * @param backdrop 采样源；默认取 [LocalGlassBackdrop]（由 GlassScaffold 提供），为 null 时退化为实色。
 */
@Composable
fun GlassSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    canSwipeToDismiss: Boolean = true,
) {
    val barClearance = LocalFloatingBarContentPadding.current
    // 宿主内部已有 12dp 底边距，这里只补差额，提示条底缘正好落在底栏净空线上
    val bottomOffset = (barClearance - SnackbarHostBottomPadding).coerceAtLeast(0.dp)
    SnackbarHost(
        state = state,
        modifier = modifier.padding(bottom = bottomOffset),
        canSwipeToDismiss = canSwipeToDismiss,
    ) { data ->
        GlassSnackbar(data = data, backdrop = backdrop)
    }
}

/**
 * 玻璃提示条本体：内容宽度自适应（1-2 行），背景实时采样下的内容做模糊与提亮。
 * 交互（水平滑动消除、超时关闭）仍由 [SnackbarHost] 负责，这里只画外观。
 */
@Composable
private fun GlassSnackbar(
    data: SnackbarData,
    backdrop: LayerBackdrop?,
) {
    val visuals = data.visuals
    val scope = rememberCoroutineScope()
    val surface = MiuixTheme.colorScheme.surface
    val contentColor = MiuixTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
                liveRegion = LiveRegionMode.Polite
            }
            .dropShadow(
                shape = GlassSnackbarShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = 0.1f,
                ),
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { GlassSnackbarShape },
                        effects = {
                            // 采样区域要比胶囊大一圈，边缘才不会取到自身而糊成灰边
                            padding = maxOf(padding, 40.dp.toPx())
                            vibrancy()
                            blur(GlassSnackbarBlurRadius.toPx(), GlassSnackbarBlurRadius.toPx())
                        },
                        onDrawSurface = {
                            drawRect(surface.copy(alpha = GLASS_SNACKBAR_SURFACE_ALPHA))
                        },
                    )
                } else {
                    Modifier.background(surface.copy(alpha = GLASS_SNACKBAR_OPAQUE_ALPHA), GlassSnackbarShape)
                },
            )
            // 吃掉点击，避免提示条把点击传给下层内容
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = visuals.message,
                color = contentColor,
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp),
            )

            // actionLabel 是跨模块的 public 属性，无法智能转换，先取到局部变量
            val actionLabel = visuals.actionLabel
            if (!actionLabel.isNullOrEmpty()) {
                val onAction by rememberUpdatedState(data::performAction)
                TextButton(
                    text = actionLabel,
                    onClick = { scope.launch { onAction() } },
                    modifier = Modifier.padding(start = 12.dp),
                    cornerRadius = 50.dp,
                    minWidth = 26.dp,
                    minHeight = 26.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    textStyle = TextStyle(fontSize = 15.sp),
                )
            }
        }
    }
}
