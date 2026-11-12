// SPDX-License-Identifier: Apache-2.0
// 玻璃表面的唯一决策点：先由角色决定「画什么」，再由档位决定「降级成什么」，
// 两者合成一份纯数值的形态计划；Composable 只负责把计划铺成 Modifier。
// 表面（底栏、操作胶囊、页签胶囊、提示条、工具栏、顶栏）只声明角色，
// 不再各自手抄三态、采样余量与 padding 顺序。

package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.effect.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight

/**
 * 玻璃表面的角色：决定「理想形态」——磨砂半径、折射带、边缘高光、底衬不透明度、采样余量，
 * 不决定降级；降级只由 [GlassTier] 决定。
 *
 * 新增表面时先在这里选角色，不要另抄一套光学链路。手写链路只剩两处，都在底栏：
 * - BottomBarActiveTabsLayer：不画可见像素，只供 tabsBackdrop 录制选中态内容；
 * - BottomBarIndicatorLayer：透镜高度/强度（10dp/14dp）、高光与内阴影都随按下进度连续变化，
 *   角色表只描述静态形态，收不住这组参数，因此仍手写。
 *
 * [TopAppBar] 的磨砂由渐进模糊承担（GlassScaffold.kt 的 topBarProgressiveBlur），
 * 这里只提供「是否采样」与实色兜底不透明度。
 */
internal enum class GlassRole { TopAppBar, Toolbar, BottomBar, FloatingActionBar, Capsule, Snackbar, SearchField }

/**
 * 角色表里一行的理想形态。
 *
 * 单位统一为 dp 与不透明度（纯数值，不含 Compose 类型），因此角色表与降级矩阵可以直接断言，
 * 不需要起 Compose 运行时。磨砂分两列：[liquidBlurDp] 是液态玻璃档的细磨砂，
 * [blurTierBlurDp] 是靠采样支撑的档位的重磨砂（无折射可依赖，只能靠更重的模糊保住层次）。
 *
 * @param effectPaddingDp 透镜采样需要的录制余量，必须不小于 [lensAmountDp]，否则会采到未录制的边缘
 * @param solidBorder 实色兜底时是否描 1dp 轮廓（目前只有浮在正文上的操作胶囊需要）
 */
internal data class GlassRoleSpec(
    val effectPaddingDp: Float = 0f,
    val liquidBlurDp: Float = 0f,
    val blurTierBlurDp: Float = 0f,
    val lensHeightDp: Float = 0f,
    val lensAmountDp: Float = 0f,
    val highlightAlpha: Float = 0f,
    val liquidScrimAlpha: Float = 0f,
    val blurScrimAlpha: Float = 0f,
    val solidScrimAlpha: Float = 0f,
    val vibrancy: Boolean = false,
    val solidBorder: Boolean = false,
)

/** 降级后的实际形态：调用方只按这份计划铺像素，不再回头判断档位。 */
internal data class GlassSurfacePlan(
    val drawsBackdrop: Boolean = false,
    val blurRadiusDp: Float = 0f,
    val lensRefractionHeightDp: Float = 0f,
    val lensRefractionAmountDp: Float = 0f,
    val vibrancy: Boolean = false,
    val highlightAlpha: Float = 0f,
    val scrimAlpha: Float = 0f,
    val border: Boolean = false,
    val effectPaddingDp: Float = 0f,
)

/**
 * 角色表：数值与各表面此前的独立配方逐项一致，因此切换过来是纯去重、不改视觉。
 *
 * 底栏与操作胶囊的细磨砂同为 4dp、折射同为 24dp 量级；页签胶囊更小更薄；
 * 提示条与工具栏没有折射（都不是浮在可滚动内容上的小块）；顶栏交给渐进模糊。
 */
internal fun glassRoleSpec(role: GlassRole): GlassRoleSpec = when (role) {
    GlassRole.TopAppBar -> GlassRoleSpec(
        liquidBlurDp = 10f,
        blurTierBlurDp = 10f,
        solidScrimAlpha = 1f,
    )

    GlassRole.Toolbar -> GlassRoleSpec(
        liquidBlurDp = 22f,
        blurTierBlurDp = 22f,
        highlightAlpha = 1f,
        liquidScrimAlpha = 0.78f,
        blurScrimAlpha = 0.78f,
        solidScrimAlpha = 1f,
    )

    GlassRole.BottomBar -> GlassRoleSpec(
        effectPaddingDp = 40f,
        liquidBlurDp = 4f,
        blurTierBlurDp = 25f,
        lensHeightDp = 24f,
        lensAmountDp = 24f,
        highlightAlpha = 0.75f,
        liquidScrimAlpha = 0.4f,
        blurScrimAlpha = 0.65f,
        solidScrimAlpha = 1f,
        vibrancy = true,
    )

    GlassRole.FloatingActionBar -> GlassRoleSpec(
        effectPaddingDp = 40f,
        liquidBlurDp = 4f,
        blurTierBlurDp = 25f,
        lensHeightDp = 12f,
        lensAmountDp = 12f,
        highlightAlpha = 1f,
        liquidScrimAlpha = 0.4f,
        blurScrimAlpha = 0.65f,
        solidScrimAlpha = 1f,
        vibrancy = true,
        solidBorder = true,
    )

    GlassRole.Capsule -> GlassRoleSpec(
        effectPaddingDp = 32f,
        liquidBlurDp = 8f,
        blurTierBlurDp = 8f,
        lensHeightDp = 12f,
        lensAmountDp = 10f,
        highlightAlpha = 1f,
        liquidScrimAlpha = 0.55f,
        blurScrimAlpha = 0.75f,
        solidScrimAlpha = 0.75f,
        vibrancy = true,
    )

    GlassRole.Snackbar -> GlassRoleSpec(
        effectPaddingDp = 40f,
        liquidBlurDp = 20f,
        blurTierBlurDp = 20f,
        highlightAlpha = 0f,
        liquidScrimAlpha = 0.68f,
        blurScrimAlpha = 0.68f,
        solidScrimAlpha = 0.95f,
        vibrancy = true,
    )

    /**
     * 顶栏搜索行：磨砂半径与顶栏渐进模糊一致（10dp），避免两层采样在胶囊边缘出现模糊跳变；
     * 底衬按可读性优先给到 0.72，降级档再抬到 0.88，实色档回到不透明。
     * 折射 12dp 小于采样余量 24dp，满足采样不越界。
     */
    GlassRole.SearchField -> GlassRoleSpec(
        effectPaddingDp = 24f,
        liquidBlurDp = 10f,
        blurTierBlurDp = 18f,
        lensHeightDp = 12f,
        lensAmountDp = 12f,
        highlightAlpha = 0.5f,
        liquidScrimAlpha = 0.72f,
        blurScrimAlpha = 0.88f,
        solidScrimAlpha = 1f,
    )
}

/**
 * 降级矩阵：整个 codebase 里唯一一处 `when (tier)`。
 *
 * 液态玻璃保留全部光学项，并把「采样余量不小于折射量」这条顺序不变量机械化；
 * 模糊档只留采样、磨砂与角色指定的底衬，折射与高光归零（没有着色器时它们本来就不生效）；
 * 实色档不采样，只铺底衬与可选描边。
 */
internal fun resolveGlassSurface(spec: GlassRoleSpec, tier: GlassTier): GlassSurfacePlan = when (tier) {
    GlassTier.LiquidGlass -> GlassSurfacePlan(
        drawsBackdrop = true,
        blurRadiusDp = spec.liquidBlurDp,
        lensRefractionHeightDp = spec.lensHeightDp,
        lensRefractionAmountDp = spec.lensAmountDp,
        vibrancy = spec.vibrancy,
        highlightAlpha = spec.highlightAlpha,
        scrimAlpha = spec.liquidScrimAlpha,
        border = false,
        effectPaddingDp = maxOf(spec.effectPaddingDp, spec.lensAmountDp),
    )

    GlassTier.Blur -> GlassSurfacePlan(
        drawsBackdrop = true,
        blurRadiusDp = spec.blurTierBlurDp,
        scrimAlpha = spec.blurScrimAlpha,
    )

    GlassTier.Solid -> GlassSurfacePlan(
        drawsBackdrop = false,
        scrimAlpha = spec.solidScrimAlpha,
        border = spec.solidBorder,
    )
}

/** 按角色取理想形态再降级。 */
internal fun resolveGlassSurface(role: GlassRole, tier: GlassTier): GlassSurfacePlan =
    resolveGlassSurface(glassRoleSpec(role), tier)

/** 皮肤风格版：ColorOS 均匀磨砂只改配方，不改降级矩阵。 */
internal fun glassRoleSpec(role: GlassRole, style: GlassStyle): GlassRoleSpec {
    val spec = glassRoleSpec(role)
    return when (style) {
        GlassStyle.Liquid -> spec
        GlassStyle.Frosted -> spec.copy(
            liquidBlurDp = spec.blurTierBlurDp,
            lensHeightDp = 0f,
            lensAmountDp = 0f,
            effectPaddingDp = 0f,
            highlightAlpha = 0f,
            liquidScrimAlpha = spec.blurScrimAlpha,
            vibrancy = false,
        )
    }
}

internal fun resolveGlassSurface(role: GlassRole, tier: GlassTier, style: GlassStyle): GlassSurfacePlan =
    resolveGlassSurface(glassRoleSpec(role, style), tier)

/**
 * 把角色与降级结果铺成表面 Modifier：调用方只交出采样源、角色、底衬色与形状。
 *
 * 档位在这里判一次（[resolveGlassTier] 内聚了「有无采样源」与「有无运行时着色器」），
 * 因此没有任何表面需要自己再问 [isRuntimeShaderSupported]。
 *
 * @param tint 底衬色：传不透明色即可，不透明度由角色的计划决定
 * @param liquidScrimAlpha 只覆盖液态玻璃档的底衬不透明度；同角色有多种强调程度时用它
 *   （如页签胶囊选中 0.92、未选中 0.55），其余档位不受影响
 * @param highlight 边缘高光提供者。刻意传 lambda 而非值：底栏按压高光逐帧变化，
 *   在绘制阶段读 State 才能避免每帧重组
 * @param borderColor 实色兜底的描边色，仅在角色要求描边且为实色档时生效
 */
@Composable
internal fun Modifier.glassSurface(
    backdrop: Backdrop?,
    role: GlassRole,
    tint: Color,
    shape: Shape,
    requested: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
    liquidScrimAlpha: Float? = null,
    highlight: (() -> Highlight?)? = { glassEdgeHighlight },
    borderColor: Color? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val style = LocalGlassStyle.current
    val tier = resolveGlassTier(
        hasBackdrop = backdrop != null,
        shaderSupported = isRuntimeShaderSupported(),
        requested = requested,
        style = style,
    )
    val plan = resolveGlassSurface(role, tier, style).let { resolved ->
        if (liquidScrimAlpha != null && resolved.drawsBackdrop && resolved.lensRefractionAmountDp > 0f) {
            resolved.copy(scrimAlpha = liquidScrimAlpha)
        } else {
            resolved
        }
    }
    val scrim = tint.copy(alpha = plan.scrimAlpha)

    if (!plan.drawsBackdrop || backdrop == null) {
        val solid = background(scrim, shape)
        return if (plan.border && borderColor != null) {
            solid.border(1.dp, borderColor, shape)
        } else {
            solid
        }
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (plan.effectPaddingDp > 0f) padding = maxOf(padding, plan.effectPaddingDp.dp.toPx())
            if (plan.vibrancy) vibrancy()
            if (plan.blurRadiusDp > 0f) blur(plan.blurRadiusDp.dp.toPx())
            if (plan.lensRefractionAmountDp > 0f) {
                lens(
                    refractionHeight = plan.lensRefractionHeightDp.dp.toPx(),
                    refractionAmount = plan.lensRefractionAmountDp.dp.toPx(),
                )
            }
        },
        highlight = if (plan.highlightAlpha > 0f && highlight != null) {
            { highlight.invoke()?.copy(alpha = plan.highlightAlpha) }
        } else {
            null
        },
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(scrim) },
    )
}
