// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Item
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.core.ChapterLabels
import com.jm3.core.model.Chapter
import com.jm3.core.reader.ChapterGroups
import top.yukonga.miuix.kmp.basic.Icon

/**
 * 章节区的一行：组头与每一话各自成项，交给 LazyColumn 逐行虚拟化。
 *
 * 章节树原先装在单个 item 里，整棵树上百行都要在首帧组合，滚动时也不参与复用；
 * 拆成行后 [CouiChapterRowCard] 用 [CouiGroupRow] 拼回同一张分段卡。
 */
internal sealed interface CouiChapterRow {
    val key: String
    val contentType: String

    data class GroupHeader(
        val groupId: String,
        val title: String,
        val summary: String,
        val expanded: Boolean,
        val showToggle: Boolean,
    ) : CouiChapterRow {
        override val key: String get() = "chapter-group-$groupId"
        override val contentType: String get() = "chapter-group"
    }

    data class ChapterLine(
        val chapter: Chapter,
        val title: String,
        val marker: String?,
        val highlighted: Boolean,
    ) : CouiChapterRow {
        override val key: String get() = "chapter-${chapter.id}"
        override val contentType: String get() = "chapter"
    }

    data object Empty : CouiChapterRow {
        override val key: String get() = "chapters-empty"
        override val contentType: String get() = "chapters-empty"
    }
}

/** 章节区的纯计算产物：分组、展开态与阅读标记都在这里算完，Compose 只负责摆行。 */
internal data class CouiChaptersPlan(
    val totalChapters: Int,
    val rows: List<CouiChapterRow>,
    val collapsibleGroupIds: List<String>,
    val allExpanded: Boolean,
    val showBatchAction: Boolean,
)

private const val CHAPTER_GROUP_COLLAPSE_HINT_EXPAND = "点击展开"
private const val CHAPTER_GROUP_COLLAPSE_HINT_COLLAPSE = "点击收起"

/** 把章节列表摊平成行计划；同一入参（含集合内容）必得同一结果。 */
internal fun planCouiChapters(
    chapters: List<Chapter>,
    expandedGroups: Set<String>,
    resumeId: String?,
    readPhotoIds: Set<String>,
): CouiChaptersPlan {
    val groups = ChapterGroups.of(chapters, Chapter::id)
    val collapsible = groups.filter { it.items.size > 1 }
    val allExpanded = collapsible.isNotEmpty() && collapsible.all { it.id in expandedGroups }
    val rows = ArrayList<CouiChapterRow>(chapters.size + groups.size + 1)
    if (groups.isEmpty()) {
        rows += CouiChapterRow.Empty
    }
    groups.forEach { group ->
        val expanded = group.id in expandedGroups
        val showToggle = group.items.size > 1
        rows += CouiChapterRow.GroupHeader(
            groupId = group.id,
            title = ChapterLabels.groupTitle(group.items.map { it.title }, group.startPosition),
            summary = if (showToggle) {
                "${group.items.size} 话 · " +
                    if (expanded) CHAPTER_GROUP_COLLAPSE_HINT_COLLAPSE else CHAPTER_GROUP_COLLAPSE_HINT_EXPAND
            } else {
                ""
            },
            expanded = expanded,
            showToggle = showToggle,
        )
        if (!showToggle || expanded) {
            group.items.forEachIndexed { indexInGroup, chapter ->
                val position = group.startPosition + indexInGroup
                rows += CouiChapterRow.ChapterLine(
                    chapter = chapter,
                    title = ChapterLabels.chapterTitle(chapter.title, position),
                    marker = when {
                        chapter.id == resumeId -> "上次读到"
                        chapter.id in readPhotoIds -> "已读"
                        else -> null
                    },
                    highlighted = chapter.id == resumeId,
                )
            }
        }
    }
    return CouiChaptersPlan(
        totalChapters = chapters.size,
        rows = rows,
        collapsibleGroupIds = collapsible.map { it.id },
        allExpanded = allExpanded,
        showBatchAction = collapsible.size > 1,
    )
}

/**
 * 批量展开/收起要点名的组：与 [CouiChaptersPlan.allExpanded] 同向的组才需要翻转。
 *
 * 文案是「全部收起」时所有可折叠组都已展开，只翻已展开的；否则只翻还没展开的组，
 * 点一次补齐剩下的，不会顺手把已展开的组关掉。传入的 [onToggleGroup] 是逐个翻转，
 * 所以这里必须按「当前是否展开」筛出目标，不能无差别地全量翻转。
 */
internal fun couiChapterBatchToggleIds(
    plan: CouiChaptersPlan,
    expandedGroups: Set<String>,
): List<String> = plan.collapsibleGroupIds.filter { (it in expandedGroups) == plan.allExpanded }

/**
 * 把 [plan] 的每一行下发成 LazyColumn 项：标题行用 [CouiGroupTitle]，行卡用 [CouiGroupRow]。
 *
 * @param expandedGroups 仅用于批量展开/收起的差量计算（与共享 holder 的展开集合同源）
 */
internal fun LazyListScope.couiChapterItems(
    plan: CouiChaptersPlan,
    expandedGroups: Set<String>,
    onToggleGroup: (groupId: String) -> Unit,
    onRead: (chapter: Chapter) -> Unit,
    onDownload: (chapter: Chapter, title: String) -> Unit,
) {
    item(key = "chapters-title", contentType = "chapters-title") {
        CouiGroupTitle(
            title = if (plan.totalChapters > 0) "章节（${plan.totalChapters}）" else "章节",
            actions = {
                if (plan.showBatchAction) {
                    val scheme = CouiKitTheme.colorScheme
                    Text(
                        text = if (plan.allExpanded) "全部收起" else "全部展开",
                        kind = CouiTextKind.Category,
                        color = scheme.accent,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CouiCapsuleShape)
                            .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, minTouchTarget = CouiMinTouchTarget) {
                                couiChapterBatchToggleIds(plan, expandedGroups).forEach { groupId ->
                                    onToggleGroup(groupId)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            },
        )
    }
    itemsIndexed(
        items = plan.rows,
        key = { _, row -> row.key },
        contentType = { _, row -> row.contentType },
    ) { index, row ->
        CouiChapterRowCard(
            row = row,
            isFirst = index == 0,
            isLast = index == plan.rows.lastIndex,
            onToggleGroup = onToggleGroup,
            onRead = onRead,
            onDownload = onDownload,
        )
    }
}

@Composable
private fun CouiChapterRowCard(
    row: CouiChapterRow,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleGroup: (groupId: String) -> Unit,
    onRead: (chapter: Chapter) -> Unit,
    onDownload: (chapter: Chapter, title: String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    when (row) {
        is CouiChapterRow.GroupHeader -> CouiGroupRow(isFirst = isFirst, isLast = isLast) {
            Item(
                title = {
                    Text(
                        text = row.title,
                        kind = CouiTextKind.PreferenceTitle,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                summary = if (row.showToggle) {
                    {
                        Text(
                            text = row.summary,
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
                trailing = if (row.showToggle) {
                    {
                        Text(
                            text = if (row.expanded) "收起" else "展开",
                            kind = CouiTextKind.PreferenceValue,
                            color = scheme.accent,
                        )
                    }
                } else {
                    {}
                },
                onClick = if (row.showToggle) {
                    { onToggleGroup(row.groupId) }
                } else {
                    null
                },
            )
        }

        is CouiChapterRow.ChapterLine -> CouiGroupRow(isFirst = isFirst, isLast = isLast, showDivider = false) {
            Divider(startIndent = 32.dp, endIndent = 16.dp)
            Item(
                contentPadding = PaddingValues(start = 32.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                title = {
                    Text(
                        text = row.title,
                        kind = CouiTextKind.Body,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                summary = row.marker?.let { marker ->
                    {
                        Text(
                            text = marker,
                            kind = CouiTextKind.Description,
                            color = if (row.highlighted) scheme.accent else scheme.onSurfaceVariant,
                        )
                    }
                },
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .couiPressable(shape = CircleShape, minTouchTarget = CouiMinTouchTarget) {
                                onDownload(row.chapter, row.title)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CouiIcons.Download,
                            contentDescription = "下载 " + row.title,
                            modifier = Modifier.size(18.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onRead(row.chapter) },
            )
        }

        CouiChapterRow.Empty -> CouiGroupRow(isFirst = isFirst, isLast = isLast) {
            Item(
                title = {
                    Text(
                        text = "暂无章节",
                        kind = CouiTextKind.PreferenceTitle,
                        color = scheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}
