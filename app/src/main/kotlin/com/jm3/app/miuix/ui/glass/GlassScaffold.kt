// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏玻璃底色不透明度：过低会让标题与滚动内容糊在一起，过高则失去通透感。 */
private const val TOP_BAR_TINT_ALPHA = 0.72f

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
                        bottom = innerPadding.calculateBottomPadding(),
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
                            // SDF 边缘高光：顶栏矩形只露出下缘一条细高光带，
                            // 与主底栏同一光源语言
                            Modifier.drawBackdrop(
                                backdrop = backdrop!!,
                                shape = { RectangleShape },
                                effects = { blur(24.dp.toPx(), 24.dp.toPx()) },
                                highlight = { glassEdgeHighlight },
                                onDrawSurface = { drawRect(containerColor.copy(alpha = TOP_BAR_TINT_ALPHA)) },
                            )
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
