// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.runtime.Immutable

/**
 * 顶栏玻璃计划：一次解析的结果，模糊层与降级实色都从这份计划取值。
 *
 * 顶栏的磨砂由 [GlassScaffold] 内部的渐进模糊承担，不画 `glassSurface`；但它仍要向角色表问两件事——
 * 这一档要不要采样源、实色兜底该多不透明。此前这两问散在脚手架的解析处与绘制分支里，同一次判定
 * 被解析两遍；现在统一收在这里，绘制路径只读字段，不再回查 [resolveGlassSurface]。
 *
 * @param tier 实际画到哪一级（[resolveGlassTier] 的唯一结论）
 * @param drawsBackdrop 是否要采样源：等价于 `tier.drawsBackdrop`，此处固化下来给绘制分支用
 * @param scrimColorAlpha 顶栏整层底衬不透明度：实色档不透明，模糊与液态档交给模糊层，取值来自角色表
 * @param blurTintAlpha 渐进模糊层混入的表面色不透明度，唯一来源是 [TOP_BAR_BLUR_TINT_ALPHA]
 */
@Immutable
internal data class TopBarGlass(
    val tier: GlassTier,
    val drawsBackdrop: Boolean,
    val scrimColorAlpha: Float,
    val blurTintAlpha: Float,
)

/** 渐进模糊层混入的表面色不透明度：标题可读性与官方 BlurredBar 配方共同的唯一来源。 */
internal const val TOP_BAR_BLUR_TINT_ALPHA = 0.3f

/**
 * 解析顶栏玻璃：先由「有无采样源 + 有无着色器 + 皮肤配方」判档位，再向角色表要这一档的形态。
 *
 * 纯函数：不读 CompositionLocal、不碰采样源实例，因此档位与透明度可以脱离 Compose 直接断言。
 */
internal fun resolveTopBarGlass(
    hasBackdrop: Boolean,
    shaderSupported: Boolean,
    style: GlassStyle,
): TopBarGlass {
    val tier = resolveGlassTier(
        hasBackdrop = hasBackdrop,
        shaderSupported = shaderSupported,
        style = style,
    )
    val plan = resolveGlassSurface(GlassRole.TopAppBar, tier, style)
    return TopBarGlass(
        tier = tier,
        drawsBackdrop = plan.drawsBackdrop,
        scrimColorAlpha = plan.scrimAlpha,
        blurTintAlpha = TOP_BAR_BLUR_TINT_ALPHA,
    )
}
