// SPDX-License-Identifier: Apache-2.0
// 液态玻璃胶囊的共享底座：筛选页签与顶栏文字动作共用同一套光学链路与三层降级，
// 保证同一屏内的胶囊形状、折射强度与降级行为完全一致。

package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.LocalGlassBackdrop
import com.jm3.app.miuix.ui.glass.glassEdgeHighlight
import com.jm3.app.miuix.ui.glass.lens
import com.jm3.app.miuix.ui.glass.vibrancy
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊圆角：等于胶囊半高（文字行高 + 上下 6dp），因此呈完整胶囊形。 */
internal val GlassCapsuleCornerRadius = 16.dp

/** 磨砂半径：与底栏、页签容器的 8dp 一致。 */
private val GlassCapsuleBlurRadius = 8.dp

/** 透镜折射带高度与偏移：与底栏同款，胶囊边缘因此有可见的弯折。 */
private val GlassCapsuleLensHeight = 12.dp
private val GlassCapsuleLensAmount = 10.dp

/** 透镜采样需要比折射带更宽的录制余量，避免采到未录制的边缘。 */
private val GlassCapsuleEffectPadding = 32.dp

/** 有采样源但无运行时着色器时的半透明实色兜底：既保住层次，也不会透到正文上。 */
private val GlassCapsuleFallbackAlpha = 0.75f

/** 胶囊形状：常量圆角，跨重组复用。 */
@Composable
private fun glassCapsuleShape(): Shape = remember { RoundedCornerShape(GlassCapsuleCornerRadius) }

/**
 * 胶囊底衬：液态玻璃三层降级。
 *
 * 光学位移链路与底栏、顶栏页签容器保持一致：`vibrancy()` 提饱和 → `blur(8dp)` 磨砂 →
 * `lens()` 边缘折射 → [glassEdgeHighlight] 双峰边缘高光 → [tintAlpha] 铺一层主题色底衬。
 * 无采样源退回实色；有采样源但无 RuntimeShader 退回半透明实色；两者都在才走折射。
 *
 * 采样源来自 [com.jm3.app.miuix.ui.glass.GlassScaffold] 的内容层，因此使用方必须位于
 * 顶栏（含 `bottomContent`、`actions` 槽）里；放进可滚动正文会把自身录进采样，出现重影。
 *
 * @param tint 底衬色：主色表示动作/选中，容器色表示中性
 * @param tintAlpha 底衬不透明度；液态玻璃下压到 0.55~0.92 让折射显影
 */
@Composable
internal fun glassCapsuleSurface(tint: Color, tintAlpha: Float): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val shaderReady = backdrop != null && isRuntimeShaderSupported()
    val shape = glassCapsuleShape()

    return when {
        backdrop == null -> Modifier.background(tint, shape)

        shaderReady -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                padding = maxOf(padding, GlassCapsuleEffectPadding.toPx())
                vibrancy()
                blur(GlassCapsuleBlurRadius.toPx(), GlassCapsuleBlurRadius.toPx())
                lens(
                    refractionHeight = GlassCapsuleLensHeight.toPx(),
                    refractionAmount = GlassCapsuleLensAmount.toPx(),
                )
            },
            highlight = { glassEdgeHighlight },
            onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha)) },
        )

        else -> Modifier.background(tint.copy(alpha = GlassCapsuleFallbackAlpha), shape)
    }
}

/**
 * 玻璃顶栏里的文字动作胶囊：与筛选页签同形状、同材质，替代顶栏里孤立的实心文字按钮。
 *
 * 触觉反馈由调用方负责，与 [GlassChipRow] 的约定一致。
 *
 * @param text 按钮文案，单行
 * @param onClick 点击回调
 * @param modifier 调用方定位
 * @param emphasized 主要动作用主色胶囊反白文字，次要动作（如「更多」）用容器色
 */
@Composable
fun GlassActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val colorScheme = MiuixTheme.colorScheme
    val tint = if (emphasized) colorScheme.primary else colorScheme.surfaceContainer
    val surfaceModifier = glassCapsuleSurface(
        tint = tint,
        tintAlpha = if (emphasized) 0.92f else 0.55f,
    )

    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        color = if (emphasized) colorScheme.onPrimary else colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .pressScale(hapticFeedback = false)
            .then(surfaceModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
