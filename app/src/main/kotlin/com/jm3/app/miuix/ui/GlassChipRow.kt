// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 筛选胶囊圆角：等于胶囊半高（文字行高 + 上下 6dp），因此呈完整胶囊形。 */
private val GlassChipCornerRadius = 16.dp

/**
 * 顶栏下方的筛选胶囊条：每个选项是一枚独立采样的液态玻璃胶囊，可横向滑动。
 *
 * 为什么每枚胶囊各自采样而不是整条套一层玻璃：选项宽度随文案变化、条内留白大于胶囊之间的
 * 8dp 间隙，整层玻璃会在间隙里露出实色底衬，看起来像一条灰带；逐枚采样则每枚只覆盖自身，
 * 间隙透出的是正文本身。
 *
 * 光学位移链路与底栏、页签容器保持一致：`vibrancy()` 提饱和 → `blur(8dp)` 磨砂 →
 * `lens()` 边缘折射（12dp 折射带 / 10dp 偏移）→ `glassEdgeHighlight` 双峰边缘高光 →
 * 底衬用主题色半透明铺一层。三层降级：无采样源退回实色；有采样源但无 RuntimeShader 退回
 * 75% 半透明实色；两者都在才走折射。三层都不会出现透到正文上的不可读状态。
 *
 * 采样源来自 [GlassScaffold] 的内容层，因此胶囊必须放在顶栏（或其 `bottomContent` 槽）里，
 * 与列表同处一棵子树时 drawBackdrop 会把胶囊自身也录进去，出现重影。
 *
 * @param tabs 选项文案
 * @param selectedTabIndex 当前选中项
 * @param onTabSelected 切换选中项回调（触觉反馈由调用方负责，与既有页签调用点一致）
 * @param modifier 调用方定位；横向留白交给 [contentPadding]，这样滑动时胶囊能贴到内容区边缘
 * @param contentPadding 胶囊条的内容内边距
 */
@Composable
fun GlassChipRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs.size) { index ->
            GlassChip(
                text = tabs[index],
                selected = index == selectedTabIndex,
                onClick = { if (index != selectedTabIndex) onTabSelected(index) },
            )
        }
    }
}

@Composable
private fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val shaderReady = backdrop != null && isRuntimeShaderSupported()
    val colorScheme = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(GlassChipCornerRadius) }
    val tint = if (selected) colorScheme.primary else colorScheme.surfaceContainer

    val surfaceModifier = when {
        backdrop == null -> Modifier.background(tint, shape)

        shaderReady -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 透镜采样需要比折射带更宽的录制余量，避免采到未录制的边缘。
                padding = maxOf(padding, 32.dp.toPx())
                vibrancy()
                blur(8.dp.toPx(), 8.dp.toPx())
                lens(refractionHeight = 12.dp.toPx(), refractionAmount = 10.dp.toPx())
            },
            highlight = { glassEdgeHighlight },
            onDrawSurface = {
                drawRect(tint.copy(alpha = if (selected) 0.92f else 0.55f))
            },
        )

        else -> Modifier.background(tint.copy(alpha = 0.75f), shape)
    }

    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .pressScale(hapticFeedback = false)
            .then(surfaceModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
