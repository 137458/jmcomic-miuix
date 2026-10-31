// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 玻璃胶囊相对选中胶囊的外扩量：内层选中胶囊的 12dp 圆角与外层 15dp 恰好同心。 */
private val GlassTabRowInset = 3.dp

/** 外层玻璃胶囊圆角 = Miuix TabRow 默认 12dp 选中胶囊 + [GlassTabRowInset]。 */
private val GlassTabRowCornerRadius = 15.dp

/**
 * 顶栏下方的分类页签：Miuix [TabRow] 外面套一层液态玻璃胶囊，实时采样其下方内容。
 *
 * 为什么需要独立的一层玻璃：[TabRow] 自身只画透明底与选中胶囊，而顶栏的渐变模糊在页签处
 * 已经衰减到接近零（渐变零点落在顶栏下沿），列表内容滚动到页签下方时对比度不足。
 * 这里给页签容器单独采样一次 backdrop，正文从下面穿过时玻璃与会同步变化。
 *
 * 页签必须位于内容层之外（顶栏或其 `bottomContent` 槽）才能这样采样：若与列表同处一棵
 * 子树，drawBackdrop 会把这棵子树（含页签自身）一起录进胶囊，出现重影。
 *
 * 与底栏/操作胶囊共用同一套液态玻璃语言：
 * - 光学：`vibrancy()` 提饱和 → `blur(8dp)` 磨砂 → `lens()` 边缘折射（12dp 折射带 / 10dp 偏移）
 *   → `glassEdgeHighlight` 双峰边缘高光 → surfaceContainer 半透明底衬；
 * - 动效：选中胶囊由 [TabRow] 自己跟随 LazyRow 的滚动偏移，切换页签时列表动画滚动，
 *   指示器因此与内容同步滑动；页签条本身固定在浮层顶栏里，列表滚动时保持吸顶；
 * - 降级：有 backdrop 且 RuntimeShader 可用才走折射；只有 backdrop 时用 75% 实色底衬；
 *   都没有时退回不透明实色。三层都不会出现"透到正文上"的不可读状态。
 *
 * 参数与 [TabRow] 的常用默认保持一致，调用点只需替换组件名。
 *
 * @param tabs 页签文案
 * @param selectedTabIndex 当前选中页签
 * @param onTabSelected 选中回调
 * @param modifier 调用方定位与外边距（玻璃只覆盖 padding 以内的区域）
 */
@Composable
fun GlassTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalGlassBackdrop.current
    val shaderReady = backdrop != null && isRuntimeShaderSupported()
    val colorScheme = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(GlassTabRowCornerRadius) }

    val surfaceModifier = when {
        // 没有采样源（脱离 GlassScaffold）：退回不透明实色，保证页签可读。
        backdrop == null -> Modifier.background(colorScheme.surfaceContainer, shape)

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
            onDrawSurface = { drawRect(colorScheme.surfaceContainer.copy(alpha = 0.55f)) },
        )

        // 只有采样源而没有 RuntimeShader：用半透明实色底衬，仍能盖住穿过的正文。
        else -> Modifier.background(colorScheme.surfaceContainer.copy(alpha = 0.75f), shape)
    }

    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        // TabRow 默认自带不透明的 surface 底色，铺在玻璃上会把模糊整块盖掉，这里只留选中胶囊。
        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
        modifier = modifier
            .then(surfaceModifier)
            .padding(GlassTabRowInset),
    )
}
