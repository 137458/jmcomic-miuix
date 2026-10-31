// SPDX-License-Identifier: Apache-2.0
// 液态玻璃操作胶囊：一枚胶囊内并排若干图标按钮，用于首页右下角的快捷动作。
//
// 三态降级与悬浮底栏保持同一套判定与光学参数：LiquidGlass 走 backdrop 折射 + 透镜，
// Blur 只采样模糊，None 退回实色加细边框；没有采样源时不会出现无法阅读的透明浮层。

package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 胶囊内的一枚图标动作。
 *
 * @param rotationDegrees 图标顺时针旋转角度；用于把库里已有的方向图标（如 Back）转到需要的朝向。
 */
data class GlassFabItem(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val rotationDegrees: Float = 0f,
)

/** 胶囊高度：比底栏的 64dp 矮一档，右下角不抢底栏的视觉重心。 */
private val GlassFabCapsuleHeight = 52.dp

/** 上下内边距：52dp 高度减去它是 44dp 点击区。 */
private val GlassFabVerticalPadding = 4.dp

/** 左右内边距与按钮间距。 */
private val GlassFabHorizontalPadding = 4.dp
private val GlassFabItemGap = 4.dp

/** 图标尺寸：比底栏图标小一档，保持层级。 */
private val GlassFabIconSize = 20.dp

/** 无障碍最小点击区下限。 */
private const val GLASS_FAB_MIN_ITEM_SIZE_DP = 44f

/**
 * 三态降级判定，与 [FloatingBottomBar] 的现行内联判定等价：
 * 液态玻璃需要 backdrop 与 RuntimeShader 同时可用；模糊只需要 backdrop；
 * 其余情况退回实色，保证没有采样源时仍然可读。
 */
internal fun resolveFloatingBottomBarMode(
    requested: FloatingBottomBarMode,
    hasBackdrop: Boolean,
    shaderSupported: Boolean,
): FloatingBottomBarMode = when {
    requested == FloatingBottomBarMode.LiquidGlass && hasBackdrop && shaderSupported ->
        FloatingBottomBarMode.LiquidGlass
    requested == FloatingBottomBarMode.Blur && hasBackdrop -> FloatingBottomBarMode.Blur
    else -> FloatingBottomBarMode.None
}

/** 单枚按钮的边长：铺满胶囊高度减去上下内边距，但不低于无障碍下限。 */
internal fun glassFabItemSizeDp(
    capsuleHeightDp: Float,
    verticalPaddingDp: Float,
): Float = (capsuleHeightDp - 2f * verticalPaddingDp).coerceAtLeast(GLASS_FAB_MIN_ITEM_SIZE_DP)

/** 胶囊宽度：左右内边距 + 各按钮边长 + 按钮间距；没有动作时宽度为 0（不渲染）。 */
internal fun glassFabCapsuleWidthDp(
    itemCount: Int,
    itemSizeDp: Float,
    gapDp: Float,
    horizontalPaddingDp: Float,
): Float {
    if (itemCount <= 0) return 0f
    return 2f * horizontalPaddingDp + itemCount * itemSizeDp + (itemCount - 1) * gapDp
}

/**
 * 右下角液态玻璃操作胶囊。
 *
 * 采样源来自 [LocalGlassBackdrop]（由 GlassScaffold 提供）：FAB 槽与内容层不是同一棵子树，
 * 只有拿到同一份 backdrop 才能对内容做模糊与折射。脱离 GlassScaffold 时降级为实色。
 *
 * @param items 动作列表；为空时不渲染任何内容，FAB 槽保持空位。
 * @param modifier 调用方定位用（Scaffold 不会给 FAB 加内边距）。
 */
@Composable
fun GlassFloatingActionBar(
    items: List<GlassFabItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val backdrop = LocalGlassBackdrop.current
    val mode = resolveFloatingBottomBarMode(
        requested = FloatingBottomBarMode.LiquidGlass,
        hasBackdrop = backdrop != null,
        shaderSupported = isRuntimeShaderSupported(),
    )
    val colorScheme = MiuixTheme.colorScheme
    // 与底栏同款容器色：液态玻璃压到 40% 让折射显影，模糊降到 65% 保证可读，实色兜底完全不透明。
    val containerColor = colorScheme.surfaceContainer
    val isDark = colorScheme.surface.luminance() < 0.5f
    val itemSize = glassFabItemSizeDp(
        capsuleHeightDp = GlassFabCapsuleHeight.value,
        verticalPaddingDp = GlassFabVerticalPadding.value,
    ).dp
    val capsuleWidth = glassFabCapsuleWidthDp(
        itemCount = items.size,
        itemSizeDp = itemSize.value,
        gapDp = GlassFabItemGap.value,
        horizontalPaddingDp = GlassFabHorizontalPadding.value,
    ).dp

    val surfaceModifier = when {
        backdrop != null && mode == FloatingBottomBarMode.LiquidGlass -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                // 透镜采样在形状边界外留 padding，少于 40dp 会采到未录制的边缘。
                padding = maxOf(padding, 40.dp.toPx())
                vibrancy()
                blur(4.dp.toPx(), 4.dp.toPx())
                lens(refractionHeight = 12.dp.toPx(), refractionAmount = 12.dp.toPx())
            },
            highlight = { glassEdgeHighlight },
            onDrawSurface = { drawRect(containerColor.copy(alpha = 0.4f)) },
        )
        backdrop != null && mode == FloatingBottomBarMode.Blur -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                blur(25.dp.toPx(), 25.dp.toPx())
            },
            onDrawSurface = { drawRect(containerColor.copy(alpha = 0.65f)) },
        )
        else -> Modifier
            .background(containerColor, CircleShape)
            .border(1.dp, colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
    }

    Row(
        modifier = modifier
            .width(capsuleWidth)
            .height(GlassFabCapsuleHeight)
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (isDark) 0.2f else 0.1f,
                ),
            )
            .then(surfaceModifier)
            .padding(horizontal = GlassFabHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(GlassFabItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            IconButton(
                onClick = item.onClick,
                modifier = Modifier.size(itemSize),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.contentDescription,
                    modifier = Modifier.size(GlassFabIconSize).rotate(item.rotationDegrees),
                    tint = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
