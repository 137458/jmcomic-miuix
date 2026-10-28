// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 悬浮底栏的内容净空（底栏高度 + 底边距 + 呼吸空间）。主框架展示底栏时提供 100.dp，
 * 无底栏的子页面与宽屏侧栏模式保持默认 0。GlassScaffold 把它叠加进 bottom 内边距，
 * 让列表末项能滚出胶囊上方，同时内容其余部分可穿透底栏滚动、持续被玻璃采样。
 */
val LocalFloatingBarContentPadding = compositionLocalOf { 0.dp }

/** 顶栏渐变模糊半径与过渡曲线：与 MIUIX 官方 BlurredBar 规范一致（HyperOS 沉浸式顶栏）。 */
private const val TOP_BAR_BLUR_RADIUS = 10f
private const val TOP_BAR_BLUR_CURVE = 2.2f

/**
 * 玻璃顶栏脚手架：顶栏改为覆盖层，内容穿透到状态栏下方滚动，顶栏实时采样其下方内容。
 *
 * 与 Miuix `Scaffold` 的差异只有两点：
 * 1. 顶栏不再占位，而是作为浮层叠在内容之上；
 * 2. 传给 [content] 的 top 内边距是顶栏实际高度，调用方需把它作为滚动容器的
 *    `contentPadding`（而非 `Modifier.padding`），否则内容退到顶栏下方就无法被采样。
 *
 * 非滚动内容（表单、居中空态/错误态）继续用 `Modifier.padding(innerPadding)` 即可，
 * 顶栏此时按实色兜底渲染。
 */
@Composable
fun GlassScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberBlurBackdrop()
    val shaderReady = backdrop != null && isRuntimeShaderSupported()
    val containerColor = MiuixTheme.colorScheme.surface
    // 顶栏高度由测量得出：不同页面的 TopAppBar 可能带副标题，硬编码会错位
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }
    val floatingBarContentPadding = LocalFloatingBarContentPadding.current

    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().layerBackdropSource(backdrop)) {
                content(
                    PaddingValues(
                        top = topBarHeight,
                        bottom = innerPadding.calculateBottomPadding() + floatingBarContentPadding,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height }
                    .then(
                        if (shaderReady) {
                            Modifier.topBarProgressiveBlur(backdrop!!)
                        } else {
                            Modifier.background(containerColor)
                        },
                    ),
            ) {
                topBar()
            }
        }
    }
}

/**
 * 顶栏渐变模糊：状态栏区域满额模糊，沿 Y 轴向下平滑衰减至底缘 0（无硬切边、无描边高光），
 * 底色以低透明度 surface 混入保证标题可读。与 MIUIX 官方 BlurredBar 同一配方。
 */
@Composable
private fun Modifier.topBarProgressiveBlur(backdrop: LayerBackdrop): Modifier {
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
        ),
    )
    return progressiveTextureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        gradient = ProgressiveBlur.Top.copy(curve = TOP_BAR_BLUR_CURVE),
        blurRadius = TOP_BAR_BLUR_RADIUS,
        colors = blurColors,
    )
}
