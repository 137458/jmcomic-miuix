// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.model.NovelChapterMeta

/** 小说目录分组大小：与相册章节列表保持同一节奏（十话一卡）。 */
internal const val NOVEL_CHAPTER_GROUP_SIZE = 10

/**
 * 按 [ascending] 决定目录展示顺序后每 [NOVEL_CHAPTER_GROUP_SIZE] 章切一组。
 *
 * 目录接口按旧到新下发，倒序展示时整体反转再切分，首组即最新章节所在组。
 */
internal fun novelChapterGroups(
    chapters: List<NovelChapterMeta>,
    ascending: Boolean,
): List<List<NovelChapterMeta>> {
    if (chapters.isEmpty()) return emptyList()
    val ordered = if (ascending) chapters else chapters.asReversed()
    return ordered.chunked(NOVEL_CHAPTER_GROUP_SIZE)
}

/**
 * 组标题：优先用首末章节的接口序号拼成 [首 ~ 末]；
 * 序号缺失时退回章节名，两者都没有时按条数回落，避免渲染出空标题行。
 */
internal fun novelGroupTitle(group: List<NovelChapterMeta>): String {
    if (group.isEmpty()) return ""
    val first = group.first()
    val last = group.last()
    val single = group.size == 1
    val firstSort = first.sort?.trim().orEmpty()
    val lastSort = last.sort?.trim().orEmpty()
    if (firstSort.isNotEmpty() && lastSort.isNotEmpty()) {
        return if (single || firstSort == lastSort) "#$firstSort" else "#$firstSort ~ #$lastSort"
    }
    val firstTitle = first.title.trim()
    val lastTitle = last.title.trim()
    return when {
        firstTitle.isNotEmpty() && lastTitle.isNotEmpty() ->
            if (single || firstTitle == lastTitle) firstTitle else "$firstTitle ~ $lastTitle"

        firstTitle.isNotEmpty() -> firstTitle
        lastTitle.isNotEmpty() -> lastTitle
        else -> "${group.size} 章"
    }
}
