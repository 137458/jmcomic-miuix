// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.model.NovelChapterMeta

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
