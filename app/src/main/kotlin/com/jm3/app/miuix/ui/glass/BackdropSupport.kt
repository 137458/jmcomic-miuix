// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 带底色保护与能力检测的 Backdrop 采样源。
 *
 * 1. 能力熔断：不支持 RuntimeShader 时返回 null，底栏自动降级为实色；
 * 2. 底色注入：录制首行先铺主题 surface 色，避免高斯模糊在透明区域卷积出黑边与噪点。
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 页面底色采样源：供与正文同一棵子树里的浮层（列表内页签等）使用。
 *
 * 内容层 backdrop 会把这棵子树整体录进去，页签再对它做 drawBackdrop 就会采样到自己，
 * 出现重影；因此列表内浮层只能采样"背后的页面底色"。页面底色是纯色，
 * 模糊与折射都没有视觉收益，交给 [top.yukonga.miuix.kmp.blur.drawBackdrop] 时效果链为空，
 * 只保留与悬浮层一致的边缘高光与半透明底衬，也不产生额外的采样开销。
 */
@Stable
class SolidColorBackdrop(private val color: Color) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        drawRect(color)
    }
}

/** 页面底色采样源；RuntimeShader 不可用时返回 null，浮层退回实色。 */
@Composable
fun rememberPageBackdrop(): Backdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return remember(surfaceColor) { SolidColorBackdrop(surfaceColor) }
}

/** backdrop 非空时把该节点注册为采样源，为空时保持原样。 */
fun Modifier.layerBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

