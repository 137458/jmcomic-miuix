// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.planLoad
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicks
import com.jm3.core.model.WeeklyPicksDetail
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 期数详情请求失败时的兜底文案（两皮肤原本各写一份，文本相同）。 */
internal const val WEEK_LOAD_FAILED: String = "加载失败"

/**
 * 期数列表请求失败时落到哪里。
 *
 * 两皮肤对「刷期数失败」的处置本来就不同，抽共享件时保留差异，不强行统一：
 * - [SplitAcrossSlots]：手上有期数就改写成详情失败（没有详情时才占位），一次失败只落一个槽位。
 * - [KeepContentNotice]：先看能不能保住已有内容，能保住就只弹一句提示，保不住才占屏。
 */
internal enum class WeekPeriodsFailureStyle {
    SplitAcrossSlots,
    KeepContentNotice,
}

/**
 * 切期时旧的详情内容怎么处理。
 *
 * - [KeepContent]：保留旧内容边加载边等，只有当期首次加载才显示加载态。
 * - [ClearContent]：立刻清空旧内容并显示加载态，绝不把上一期的作品当本期的先摆出来。
 */
internal enum class WeekDetailLoadingStyle {
    KeepContent,
    ClearContent,
}

/**
 * 刷新指示器由哪一号请求复位。
 *
 * - [WithDetail]：等随后的详情请求回来才收，转得久但和内容同时就绪。
 * - [WithPeriods]：期数请求一结束就收，快速收束。
 */
internal enum class WeekRefreshReset {
    WithDetail,
    WithPeriods,
}

/**
 * 每周必看的整页状态编排：取期数、按期取详情、切期、刷新、失败落点。
 *
 * 这里只有状态与会话代际号，没有任何控件；皮肤负责把状态画成各自的样子。
 * 快速切期用代际号丢弃旧响应，否则慢响应会把当前期覆盖成上一期内容。
 */
@Stable
internal class WeekScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val loadPicks: suspend () -> SessionOutcome<WeeklyPicks>,
    private val loadPicksDetail: suspend (String) -> SessionOutcome<WeeklyPicksDetail>,
    private val showMessage: suspend (message: String) -> Unit,
    private val periodsFailureStyle: WeekPeriodsFailureStyle,
    private val detailLoadingStyle: WeekDetailLoadingStyle,
    private val refreshReset: WeekRefreshReset,
    private val selectedIndex: MutableIntState,
) {
    /** 期数列表；接口不可用时为空。 */
    var periods by mutableStateOf<List<WeeklyPeriod>>(emptyList())
        private set

    /** 当前期的作品集合；还没取到时为空。 */
    var detail by mutableStateOf<WeeklyPicksDetail?>(null)
        private set

    var periodsLoading by mutableStateOf(true)
        private set

    var detailLoading by mutableStateOf(false)
        private set

    /** 期数列表的失败落点；失效与普通失败靠 [ScreenLoadPlan.phase] 区分。 */
    var periodsFailure by mutableStateOf<ScreenLoadPlan?>(null)
        private set

    /** 当期详情的失败落点。 */
    var detailFailure by mutableStateOf<ScreenLoadPlan?>(null)
        private set

    var refreshing by mutableStateOf(false)
        private set

    /** 当前选中的期数序号；皮肤只读，切换走 [selectPeriod]。 */
    val selected: Int get() = selectedIndex.intValue

    /** 当前选中的期数；期数还没到手时为空，皮肤据此决定是否画选择器。 */
    val currentPeriod: WeeklyPeriod? get() = periods.getOrNull(selected)

    // 代际号：只认最后一次请求的响应
    private var periodsToken = 0
    private var detailToken = 0

    /**
     * 取期数列表。
     *
     * 期数列表只在进入页面/刷新时取一次，切期只重取当期，避免每次切期都多打一次列表接口。
     * [showLoading] 为假时不把整屏换成加载态（下拉刷新期间要保住已有内容）。
     */
    fun loadPeriods(showLoading: Boolean = true) {
        val token = ++periodsToken
        if (showLoading) periodsLoading = true
        periodsFailure = null
        scope.launch {
            val outcome = loadPicks()
            if (token != periodsToken) return@launch
            if (refreshReset == WeekRefreshReset.WithPeriods) refreshing = false
            periodsLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> onPeriodsLoaded(outcome.value.periods)
                else -> applyPeriodsFailure(outcome)
            }
        }
    }

    /** 刷新入口：重取期数但不把整屏换成加载态，由 [WeekRefreshReset] 决定指示器何时收。 */
    fun refresh() {
        refreshing = true
        loadPeriods(showLoading = false)
    }

    /** 期数胶囊条/下拉的唯一入口：换期并重取当期详情。 */
    fun selectPeriod(index: Int) {
        val period = periods.getOrNull(index) ?: return
        if (index == selected) return
        selectedIndex.intValue = index
        // 换期先清掉上一期的内容：此刻屏上任何旧作品都属于别的期
        detail = null
        loadDetail(period)
    }

    /** 当期详情失败后的重试：落在当前选中的那一期。 */
    fun retryDetail() {
        val period = periods.getOrNull(selected) ?: return
        loadDetail(period)
    }

    private fun onPeriodsLoaded(picks: List<WeeklyPeriod>) {
        periods = picks
        val index = selected.coerceIn(0, (periods.size - 1).coerceAtLeast(0))
        selectedIndex.intValue = index
        val period = periods.getOrNull(index)
        if (period == null) {
            // 期数被清空：旧详情不再对应当前页，整块收起
            refreshing = false
            detail = null
            detailLoading = false
        } else {
            loadDetail(period, showLoading = autoDetailShowsLoading())
        }
    }

    private fun autoDetailShowsLoading(): Boolean = when (detailLoadingStyle) {
        WeekDetailLoadingStyle.KeepContent -> detail == null
        WeekDetailLoadingStyle.ClearContent -> true
    }

    private suspend fun applyPeriodsFailure(outcome: SessionOutcome<*>) {
        refreshing = false
        when (periodsFailureStyle) {
            WeekPeriodsFailureStyle.SplitAcrossSlots -> when {
                periods.isEmpty() -> {
                    periodsFailure = planLoad(
                        outcome = outcome,
                        hasContent = false,
                        failureMessage = WEEK_LOAD_FAILED,
                    )
                }

                // 期数还在，只是这次没取到新的：改动当前期的落点，不把选择器顶掉
                detail == null -> {
                    detailFailure = planLoad(
                        outcome = outcome,
                        hasContent = false,
                        failureMessage = WEEK_LOAD_FAILED,
                    )
                }
            }

            WeekPeriodsFailureStyle.KeepContentNotice -> {
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = periods.isNotEmpty(),
                    failureMessage = WEEK_LOAD_FAILED,
                )
                if (plan.keepContent) {
                    // 有旧期数时刷新失败只降级提示，不把已有内容清空
                    periodsFailure = null
                    plan.notice?.let { notice -> showMessage(notice) }
                } else {
                    periodsFailure = plan
                }
            }
        }
    }

    private fun loadDetail(period: WeeklyPeriod, showLoading: Boolean = true) {
        val token = ++detailToken
        if (showLoading) {
            detailLoading = true
            if (detailLoadingStyle == WeekDetailLoadingStyle.ClearContent) detail = null
        }
        detailFailure = null
        scope.launch {
            val outcome = loadPicksDetail(period.id)
            if (token != detailToken) return@launch
            detailLoading = false
            if (refreshReset == WeekRefreshReset.WithDetail) refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> detail = outcome.value
                else -> detailFailure = planLoad(
                    outcome = outcome,
                    hasContent = false,
                    failureMessage = WEEK_LOAD_FAILED,
                )
            }
        }
    }
}

/**
 * 建立并记住每周必看的状态编排。
 *
 * [reloadKey] 变化时重新取期数（两皮肤的重取时机不同：一皮肤按进入页面，另一皮肤还要跟着账号走）；
 * [showLoadingOnEnter] 决定重取时是否显示整屏加载态。
 */
@Composable
internal fun rememberWeekScaffoldState(
    loadPicks: suspend () -> SessionOutcome<WeeklyPicks>,
    loadPicksDetail: suspend (String) -> SessionOutcome<WeeklyPicksDetail>,
    periodsFailureStyle: WeekPeriodsFailureStyle,
    detailLoadingStyle: WeekDetailLoadingStyle,
    refreshReset: WeekRefreshReset,
    showMessage: suspend (message: String) -> Unit = {},
    reloadKey: Any? = Unit,
    showLoadingOnEnter: Boolean = true,
): WeekScaffoldState {
    val scope = rememberCoroutineScope()
    val selectedIndex = rememberSaveable { mutableIntStateOf(0) }
    // 请求函数每次重组都是新 lambda：用 rememberUpdatedState 让状态机始终拿到最新的一份，自身不被重建
    val currentPicks = rememberUpdatedState(loadPicks)
    val currentPicksDetail = rememberUpdatedState(loadPicksDetail)
    val currentShowMessage = rememberUpdatedState(showMessage)
    val state = remember(
        scope,
        selectedIndex,
        periodsFailureStyle,
        detailLoadingStyle,
        refreshReset,
    ) {
        WeekScaffoldState(
            scope = scope,
            loadPicks = { currentPicks.value() },
            loadPicksDetail = { id -> currentPicksDetail.value(id) },
            showMessage = { message -> currentShowMessage.value(message) },
            periodsFailureStyle = periodsFailureStyle,
            detailLoadingStyle = detailLoadingStyle,
            refreshReset = refreshReset,
            selectedIndex = selectedIndex,
        )
    }
    LaunchedEffect(state, reloadKey) { state.loadPeriods(showLoading = showLoadingOnEnter) }
    return state
}
