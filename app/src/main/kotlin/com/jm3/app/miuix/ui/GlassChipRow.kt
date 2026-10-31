// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏下方的筛选胶囊条：每个选项是一枚独立采样的液态玻璃胶囊，可横向滑动。
 *
 * 为什么每枚胶囊各自采样而不是整条套一层玻璃：选项宽度随文案变化、条内留白大于胶囊之间的
 * 8dp 间隙，整层玻璃会在间隙里露出实色底衬，看起来像一条灰带；逐枚采样则每枚只覆盖自身，
 * 间隙透出的是正文本身。胶囊底衬与降级见 [glassCapsuleSurface]，同一屏内的文字动作胶囊
 * （[GlassActionChip]）共用同一份实现。
 *
 * 采样源来自 GlassScaffold 的内容层，因此胶囊必须放在顶栏（或其 `bottomContent` 槽）里，
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
    val colorScheme = MiuixTheme.colorScheme
    val surfaceModifier = glassCapsuleSurface(
        tint = if (selected) colorScheme.primary else colorScheme.surfaceContainer,
        tintAlpha = if (selected) 0.92f else 0.55f,
    )

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
