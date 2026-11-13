package com.jm3.app.miuix.reader

/** pager 目标页夹取：越界目标一律落到端点页，空 pager 退化为 0。 */
internal fun clampPagerTarget(target: Int, pageCount: Int): Int =
    target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
