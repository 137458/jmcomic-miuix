// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 视口上界缺失时的兜底高度：宁可矮一截，也不能回落到无界测量。 */
internal val NotesViewportMinHeight = 48.dp

/**
 * 更新说明滚动视口的规格。
 *
 * 只有两个自由度：多高、边缘羽化多远。皮肤差异走这两个数值，不再各写一套滚动节点。
 */
internal data class NotesViewportSpec(val maxHeight: Dp, val fade: Dp)

/**
 * 收敛视口规格。
 *
 * 上界必须是有限正数：弹窗正文槽位以无限最大高度测量子节点（COUI Dialog 的正文本身是滚动视口），
 * 内层滚动组件一旦拿到无界约束就会抛 "Vertically scrollable component was measured with an
 * infinity maximum height constraints"，因此这里把非法输入压回有限值，而不是交给运行时炸。
 * 羽化不能超过半个视口，否则两侧渐变会重叠成整块薄雾。
 */
internal fun notesViewportSpec(maxHeight: Dp, fade: Dp): NotesViewportSpec {
    val height = if (maxHeight.value.isFinite() && maxHeight.value >= NotesViewportMinHeight.value) {
        maxHeight
    } else {
        NotesViewportMinHeight
    }
    val fadeValue = if (fade.value.isFinite()) fade.value else 0f
    return NotesViewportSpec(
        maxHeight = height,
        fade = fadeValue.coerceIn(0f, height.value / 2f).dp,
    )
}

/** 弹窗内的说明视口：竖屏正文上限约 600dp，240dp 之外的部分交给视口内部滚动。 */
internal val NotesDialogViewport = notesViewportSpec(maxHeight = 240.dp, fade = 40.dp)

/** 更新页内的说明视口：页面可整屏滚动，说明区只在自己变长时受这条上限约束。 */
internal val NotesPageViewport = notesViewportSpec(maxHeight = 320.dp, fade = 32.dp)

/**
 * 说明折叠行：null 表示这一处不折叠。
 *
 * 文案由调用方决定（弹窗与页面口径不同），是否显示由 [updateNotesFold] 统一判定。
 */
internal class NotesFold(
    val expanded: Boolean,
    val label: String,
    val onToggle: () -> Unit,
)

/** 折叠行的显隐口径：展开态必然可收起；折叠态只有确实藏了内容才给出入口。 */
internal fun updateNotesFoldVisible(hiddenLineCount: Int, expanded: Boolean): Boolean =
    expanded || hiddenLineCount > 0

/** 弹窗折叠行文案：收起态把剩余行数写进按钮，用户不必展开才知道还有多少。 */
internal fun updateNotesFoldLabel(expanded: Boolean, hiddenLineCount: Int): String =
    if (expanded) "收起说明" else "展开全部（还有 $hiddenLineCount 行）"

/** 弹窗折叠行：不可折叠时返回 null，调用方无需自己判断显隐。 */
internal fun updateNotesFold(
    expanded: Boolean,
    hiddenLineCount: Int,
    onToggle: () -> Unit,
): NotesFold? = if (!updateNotesFoldVisible(hiddenLineCount, expanded)) {
    null
} else {
    NotesFold(
        expanded = expanded,
        label = updateNotesFoldLabel(expanded, hiddenLineCount),
        onToggle = onToggle,
    )
}

/**
 * 说明正文的滚动视口：恒定有限上界 + 边缘羽化 + 内部滚动。
 *
 * 三件事的顺序是契约，不能调换：
 * 1. [heightIn] 必须排在 [verticalScroll] 之前，且无论展开与否都存在——它就是那条"永远有界"的保证；
 *    条件式上界（只在展开时加 heightIn）会让折叠态以无限高度测量滚动节点，直接崩溃。
 * 2. 羽化在滚动节点外侧：放内侧时量到的是内容整高，遮罩落在滚出视口的顶部，用户看到的是硬裁切。
 * 3. 羽化只在还有内容的方向开启，滚到顶或底时对应一侧恢复实心，避免"永远有雾"的假暗示。
 */
@Composable
internal fun NotesScrollViewport(
    spec: NotesViewportSpec,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val hasContentAbove by remember { derivedStateOf { scrollState.value > 0 } }
    val hasContentBelow by remember { derivedStateOf { scrollState.maxValue > scrollState.value } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = spec.maxHeight)
            .verticalEdgeFade(
                top = hasContentAbove,
                bottom = hasContentBelow,
                fade = spec.fade,
            )
            .verticalScroll(scrollState),
        content = content,
    )
}

/**
 * 可折叠的说明区块：正文进视口，折叠行留在视口之外。
 *
 * 折叠行必须在外侧：它在视口里时会被推到整段说明的内容坐标末尾，展开后要滚几十屏才点得到，
 * 而且羽化遮罩会盖住它。正文与折叠行之间的分割线、外观由 [footer] 交给调用方，皮肤差异不外泄。
 */
@Composable
internal fun FoldableNotes(
    fold: NotesFold?,
    spec: NotesViewportSpec,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        NotesScrollViewport(spec = spec, content = content)
        if (fold != null && footer != null) {
            footer()
        }
    }
}
