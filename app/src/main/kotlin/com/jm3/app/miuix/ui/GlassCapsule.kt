// SPDX-License-Identifier: Apache-2.0
// 液态玻璃胶囊的共享底座：筛选页签与顶栏文字动作共用同一个角色。
// 光学配方与三态降级都由 glassSurface 的角色表决定，
// 保证同一屏内的胶囊形状、折射强度与降级行为完全一致。

package com.jm3.app.miuix.ui

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
import com.jm3.app.miuix.ui.glass.GlassRole
import com.jm3.app.miuix.ui.glass.LocalGlassBackdrop
import com.jm3.app.miuix.ui.glass.glassSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊圆角：等于胶囊半高（文字行高 + 上下 6dp），因此呈完整胶囊形。 */
internal val GlassCapsuleCornerRadius = 16.dp

/** 胶囊形状：常量圆角，跨重组复用。 */
@Composable
private fun glassCapsuleShape(): Shape = remember { RoundedCornerShape(GlassCapsuleCornerRadius) }

/**
 * 胶囊底衬：光学链路与三态降级都交给 [glassSurface] 的 Capsule 角色。
 *
 * 配方（8dp 磨砂、12/10dp 折射、双峰边缘高光、32dp 采样余量）与降级矩阵都在角色表里，
 * 本文件只声明「这是胶囊」并交出底衬色与不透明度，不再手抄一遍 drawBackdrop。
 *
 * 采样源来自 [com.jm3.app.miuix.ui.glass.GlassScaffold] 的内容层，因此使用方必须位于
 * 顶栏（含 `bottomContent`、`actions` 槽）里；放进可滚动正文会把自身录进采样，出现重影。
 *
 * @param tint 底衬色：主色表示动作/选中，容器色表示中性
 * @param tintAlpha 液态玻璃档的底衬不透明度；压到 0.55~0.92 让折射显影
 */
@Composable
internal fun Modifier.glassCapsuleSurface(tint: Color, tintAlpha: Float): Modifier = glassSurface(
    backdrop = LocalGlassBackdrop.current,
    role = GlassRole.Capsule,
    tint = tint,
    shape = glassCapsuleShape(),
    liquidScrimAlpha = tintAlpha,
)

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

    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        color = if (emphasized) colorScheme.onPrimary else colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .pressScale(hapticFeedback = false)
            .glassCapsuleSurface(
                tint = tint,
                tintAlpha = if (emphasized) 0.92f else 0.55f,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
