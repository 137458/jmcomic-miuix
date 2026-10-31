// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** backdrop 非空时把该节点注册为采样源，为空时保持原样。 */
fun Modifier.layerBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

