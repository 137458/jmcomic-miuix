package com.jm3.core.reader

/** 默认预取当前页之后几页。 */
const val PREFETCH_AHEAD_PAGES: Int = 3

/**
 * 一次预取的结论：回写后的页码计划，以及这次该入队的图片下标（0 基）。
 */
data class PrefetchPlan(
    val plan: ReaderPagePlan,
    val pages: List<Int>,
)

/**
 * 翻页驱动预取：先按可见下标回写页码计划，再给出「还没入队、且在当前页之后」的图片下标。
 *
 * 纵向长图与横向 pager 共用这一处：两者的可见下标含义相同（0 基），只差请求尺寸，
 * 因此「预取哪几页、哪些已经入队」不再各写一遍。跳页动画进行中沿用原计划（[visiblePage] 拒绝回写），
 * 但预取仍按目标页推进，动画落地前目标页与后续页已经就位。
 *
 * 调用方必须知道的顺序与不变式：
 * 1. [plan] 必须是回写前的那一份，返回的 [PrefetchPlan.plan] 才是回写后的；两者别混用。
 *    跳页冻结只活在返回的计划里（[ReaderPagePlan.pendingTarget]），丢掉它中间页就会把页码带跑。
 * 2. [firstVisibleIndex] 是 0 基下标，允许等于 pageCount（列表末尾补了留白项）；收敛由 [visiblePage] 负责，
 *    调用方不要先自己 coerceAtMost，也不要传 1 基页码。
 * 3. [loaded] 是调用方的去重账本，本函数只读；返回的下标由调用方记账。账本何时失效是调用方的责任：
 *    换章、页宽变化、切换纵横时必须清空，否则按新尺寸该重发的页永远不会入队。
 * 4. 返回的 [PrefetchPlan.pages] 不含当前页，也不超过 pageCount - 1；跳页进行中它们按目标页算而不是按可见页算，
 *    因此不能拿它当「可见邻域」用。
 * 5. 本函数只算下标：请求尺寸由调用方按展示同一处换算给出，尺寸不一致会让预取解出的图与缓存键错开，等于白解。
 * 6. 纯函数、不挂起、不持状态；同一处连续调用（回写计划 + 入队）保证计划与账本始终同源。
 *
 * @param firstVisibleIndex 列表或 pager 的 0 基可见下标，允许等于图片数（末尾留白项）
 * @param loaded 本次翻页周期内已经入队的下标，去重由本函数负责，调用方只管把返回的下标塞进来
 * @param ahead 当前页之后最多预取几页，<= 0 表示不预取
 */
fun advancePrefetch(
    plan: ReaderPagePlan,
    firstVisibleIndex: Int,
    loaded: Set<Int>,
    ahead: Int = PREFETCH_AHEAD_PAGES,
): PrefetchPlan {
    val next = visiblePage(plan, firstVisibleIndex)
    return PrefetchPlan(
        plan = next,
        pages = prefetchAhead(next, ahead).filterNot { it in loaded },
    )
}
