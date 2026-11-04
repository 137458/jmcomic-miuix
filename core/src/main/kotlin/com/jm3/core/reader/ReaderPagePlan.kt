package com.jm3.core.reader

/**
 * 阅读器页码的唯一真源：顶栏页码、跳页器、音量键翻页与预取都从它取页码。
 *
 * 页码一律 1 基（与顶栏 "1/12"、跳页器范围一致）；pageCount 为 0 表示本章没有图，
 * 此时 currentPage 为 0，跳页与预取都是空操作。
 *
 * 收拢前的三份真相各自夹取：screen 里的 currentPage 状态、pagerState.currentPage、
 * listState.firstVisibleItemIndex，每处都自己 `+1` 再 `coerceAtMost(total)`，
 * 跳页时还要靠注释提醒"别让可见页回写覆盖目标页"，但实现里没有任何开关。
 * 这里把"现在第几页、跳到第几页、还在跳"收成一份不可变计划，屏幕只按计划驱动 pager 与列表。
 *
 * 跳页与可见页回写是单向的：jumpTo 记下 pendingTarget 之后，[visiblePage] 在落地前拒绝
 * 任何回写，动画途经的中间页因此不会把页码带跑；[settleJump] 落地后才恢复回写。
 */
data class ReaderPagePlan(
    val pageCount: Int,
    val currentPage: Int,
    val pendingTarget: Int? = null,
) {
    init {
        require(pageCount >= 0) { "pageCount must be >= 0: $pageCount" }
        require(currentPage == 0 || currentPage in 1..pageCount) {
            "currentPage out of range: $currentPage (pageCount=$pageCount)"
        }
        require(pendingTarget == null || pendingTarget in 1..pageCount) {
            "pendingTarget out of range: $pendingTarget (pageCount=$pageCount)"
        }
    }

    /** 跳页动画进行中：此时可见页回写被拒绝。 */
    val isJumping: Boolean get() = pendingTarget != null

    /** 最后一页页码；空章为 0。 */
    val lastPage: Int get() = pageCount

    companion object {
        /** 首屏与换章后的初始计划：有图从第 1 页开始，空章停在 0。 */
        fun initial(pageCount: Int): ReaderPagePlan {
            val count = pageCount.coerceAtLeast(0)
            return ReaderPagePlan(pageCount = count, currentPage = if (count == 0) 0 else 1)
        }
    }
}

/**
 * 跳到第 [requested] 页：夹进 1..pageCount，并记下 pendingTarget 冻结回写。
 *
 * 空章返回原计划（没有可跳的页）。目标页等于当前页时仍然返回带 pendingTarget 的新计划：
 * 屏幕照旧执行一次动画，落地后 settleJump 复原，与"跳到当前页"的旧行为一致。
 */
fun jumpTo(plan: ReaderPagePlan, requested: Int): ReaderPagePlan {
    if (plan.pageCount == 0) return plan
    val target = requested.coerceIn(1, plan.pageCount)
    return plan.copy(currentPage = target, pendingTarget = target)
}

/**
 * 跳页落地：[target] 是这次动画的目标页，只有它仍是当前 pendingTarget 时才解除冻结。
 *
 * 落地前用户又跳到别处的场景里，旧动画结束不能把新目标清掉，否则新动画途中途经的中间页
 * 会顺着 [visiblePage] 写回页码。目标已被取代时返回原计划，等新动画自己落地。
 */
fun settleJump(plan: ReaderPagePlan, target: Int): ReaderPagePlan =
    if (plan.pendingTarget == target) plan.copy(currentPage = target, pendingTarget = null) else plan

/**
 * 可见页回写：[firstVisibleIndex] 是列表/pager 的 0 基下标。
 *
 * 跳页进行中原样返回；长图列表末尾补了工具栏留白项，下标记可能超出图片数，
 * 这里按 pageCount 收敛，页码不会出现 N+1/N。空章恒为 0。
 */
fun visiblePage(plan: ReaderPagePlan, firstVisibleIndex: Int): ReaderPagePlan {
    if (plan.pageCount == 0 || plan.isJumping) return plan
    val page = (firstVisibleIndex + 1).coerceIn(1, plan.pageCount)
    return if (page == plan.currentPage) plan else plan.copy(currentPage = page)
}

/** 音量键与边缘翻页的下一页：夹进 1..pageCount，空章为 0。 */
fun stepPages(plan: ReaderPagePlan, delta: Int): Int {
    if (plan.pageCount == 0) return 0
    return (plan.currentPage + delta).coerceIn(1, plan.pageCount)
}

/**
 * 当前页之后要预取的下标（0 基，最多 [ahead] 个）。
 *
 * 只算下标不算尺寸：长图按页框、左右按整屏，两处请求尺寸不同，但"预取哪几页"是同一件事。
 * 换成照片或页宽变化时屏幕重建计划并清空已预取集合，作废策略与这里同源。
 */
fun prefetchAhead(plan: ReaderPagePlan, ahead: Int = 3): List<Int> {
    if (plan.pageCount == 0 || ahead <= 0) return emptyList()
    // currentPage 是 1 基，下一张图的下标正好等于它
    val start = plan.currentPage
    val last = plan.pageCount - 1
    if (start > last) return emptyList()
    return (start..minOf(start + ahead - 1, last)).toList()
}
