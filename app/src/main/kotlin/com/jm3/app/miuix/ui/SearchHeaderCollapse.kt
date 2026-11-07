package com.jm3.app.miuix.ui

/** 搜索栏折叠后的目标方向：由滞回阈值确认，不随单帧滚动方向抖动。 */
enum class SearchHeaderIntent {
    Expanded,
    Collapsed,
}

/**
 * 搜索栏折叠状态。
 *
 * [progress] 为 0（完全展开）到 1（完全收起）；[collapseDebtPx] 与 [expandDebtPx] 是方向反转后
 * 尚未越过阈值的净位移，反向滚动会先抵消对方，避免手指轻微抖动立刻翻转意图。
 */
data class SearchHeaderCollapseState(
    val progress: Float = 0f,
    val intent: SearchHeaderIntent = SearchHeaderIntent.Expanded,
    val collapseDebtPx: Float = 0f,
    val expandDebtPx: Float = 0f,
)

/**
 * 搜索栏折叠的纯逻辑：只接受滚动增量与锁定标记，不依赖 Compose，便于单测阈值、滞回与焦点锁定。
 *
 * 约定：嵌套滚动中内容向上滚（看更后面的内容）为负增量，对应搜索栏收起；内容向下滚为正增量，对应展开。
 */
object SearchHeaderCollapseLogic {

    /** 吸附阈值的进度分界：滚动停止时越过一半即保持当前方向，吸到 0 或 1。 */
    const val SETTLE_PROGRESS_PIVOT = 0.5f

    /** 吸附时速度优先级：超过该速度按甩动方向吸附，避免慢速拖拽被小速度带偏。 */
    const val FLING_VELOCITY_PX_PER_SECOND = 200f

    /**
     * 应用一帧滚动增量。
     *
     * @param deltaPx 已消费的滚动位移，负值表示内容向上滚（搜索栏收起方向）。
     * @param heightPx 搜索栏展开态高度，用于把位移换算成进度。
     * @param locked 搜索框聚焦或搜索态展开时为 true，此时保持可见且不移动。
     * @param collapseThresholdPx 向下滚动累计超过该净位移后才开始收起。
     * @param expandThresholdPx 向上滚动累计超过该净位移后才开始展开。
     */
    fun onScroll(
        state: SearchHeaderCollapseState,
        deltaPx: Float,
        heightPx: Float,
        locked: Boolean,
        collapseThresholdPx: Float,
        expandThresholdPx: Float,
    ): SearchHeaderCollapseState {
        if (locked) {
            // 保留当前进度交给状态持有者动画展开，滚动增量一律不参与折叠。
            return state.copy(
                intent = SearchHeaderIntent.Expanded,
                collapseDebtPx = 0f,
                expandDebtPx = 0f,
            )
        }
        if (deltaPx == 0f || heightPx <= 0f) return state

        if (deltaPx < 0f) {
            val amount = -deltaPx
            val cancelled = minOf(state.expandDebtPx, amount)
            val collapseDebt = state.collapseDebtPx + (amount - cancelled)
            val expandDebt = state.expandDebtPx - cancelled
            if (state.intent == SearchHeaderIntent.Collapsed) {
                return state.copy(
                    collapseDebtPx = collapseDebt,
                    expandDebtPx = expandDebt,
                    progress = (state.progress - deltaPx / heightPx).coerceIn(0f, 1f),
                )
            }
            if (collapseDebt >= collapseThresholdPx) {
                return state.copy(
                    intent = SearchHeaderIntent.Collapsed,
                    collapseDebtPx = 0f,
                    expandDebtPx = 0f,
                    progress = (state.progress - deltaPx / heightPx).coerceIn(0f, 1f),
                )
            }
            return state.copy(collapseDebtPx = collapseDebt, expandDebtPx = expandDebt)
        }

        val amount = deltaPx
        val cancelled = minOf(state.collapseDebtPx, amount)
        val expandDebt = state.expandDebtPx + (amount - cancelled)
        val collapseDebt = state.collapseDebtPx - cancelled
        if (state.intent == SearchHeaderIntent.Expanded) {
            return state.copy(
                collapseDebtPx = collapseDebt,
                expandDebtPx = expandDebt,
                progress = (state.progress - deltaPx / heightPx).coerceIn(0f, 1f),
            )
        }
        if (expandDebt >= expandThresholdPx) {
            return state.copy(
                intent = SearchHeaderIntent.Expanded,
                collapseDebtPx = 0f,
                expandDebtPx = 0f,
                progress = (state.progress - deltaPx / heightPx).coerceIn(0f, 1f),
            )
        }
        return state.copy(collapseDebtPx = collapseDebt, expandDebtPx = expandDebt)
    }

    /**
     * 滚动停止后的吸附目标：强甩动按方向走，否则按进度是否过半决定。
     * 返回值只可能是 0f（展开）或 1f（收起）。
     */
    fun settleTarget(
        state: SearchHeaderCollapseState,
        velocityPxPerSecond: Float,
        locked: Boolean,
    ): Float {
        if (locked) return 0f
        return when {
            velocityPxPerSecond <= -FLING_VELOCITY_PX_PER_SECOND -> 1f
            velocityPxPerSecond >= FLING_VELOCITY_PX_PER_SECOND -> 0f
            state.progress >= SETTLE_PROGRESS_PIVOT -> 1f
            else -> 0f
        }
    }

    /** 吸附目标对应的最终意图，供动画结束后写回状态。 */
    fun intentForTarget(targetProgress: Float): SearchHeaderIntent =
        if (targetProgress >= 1f) SearchHeaderIntent.Collapsed else SearchHeaderIntent.Expanded

    /** 强制展开（回顶、点击再唤入口、聚焦）：进度归零并清空滞回债。 */
    fun forceExpanded(state: SearchHeaderCollapseState): SearchHeaderCollapseState =
        state.copy(
            progress = 0f,
            intent = SearchHeaderIntent.Expanded,
            collapseDebtPx = 0f,
            expandDebtPx = 0f,
        )
}
