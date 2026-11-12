package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.ReaderLoadGate
import com.jm3.core.reader.ReaderPagePlan
import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.advancePrefetch
import com.jm3.core.reader.jumpTo
import com.jm3.core.reader.resolveInitialPage
import com.jm3.core.reader.settleJump
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 进度恢复所处的阶段：三个阶段之间的先后由 [ReaderSession] 保证，调用方不能跳步。 */
enum class ReaderRestorePhase {
    /** 首帧布局（纵横模式与页宽）尚未落定：此时读到的页码会被随后的布局回写覆盖。 */
    AWAITING_LAYOUT,

    /** 布局已落定，可以读旧进度并放开写盘闸门。 */
    RESTORING,

    /** 恢复已完成，写盘闸门已打开。 */
    SETTLED,
}

/**
 * 一次恢复的结论。
 *
 * [saved] 是读到的旧进度（可空），屏幕用它决定提示文案；[targetPage] 是要跳到的页码，
 * null 表示没有可恢复的目标（历史进度非法或本章没有图）。
 */
data class ReaderRestoreOutcome(
    val saved: ReaderPageProgress?,
    val targetPage: Int?,
)

/**
 * 阅读器的页码、预取与进度恢复时序，一处持有。
 *
 * 收拢前三套阅读器各自手写同一串顺序约束：跳页要先冻结可见页回写、落地后才解冻；
 * 进度必须「先读旧值、再开写盘闸门」，反过来会先落盘首帧的第 1 页把旧进度覆盖掉；
 * 预取要按当前计划去重，换页宽或换纵横后账本必须失效。顺序写在注释里时每屏抄一遍，
 * 这里改由阶段与闸门表达，屏幕只按结论驱动 pager 与列表。
 *
 * 页码语义完全沿用 core 侧纯函数（[jumpTo]、[settleJump]、[advancePrefetch] 与
 * [resolveInitialPage]），本类不复制夹取与冻结逻辑，只维护「现在处在哪一步」。
 *
 * 纯 Kotlin：不依赖 Compose 运行时，状态以 [StateFlow] 暴露，屏幕自行 collect。
 *
 * @param pageCount 本章图片数；0 表示空章，跳页与预取都是空操作
 */
class ReaderSession(val pageCount: Int) {
    /** 章节加载代际：小说屏没有页码计划，用它替代手写的 token 自增。 */
    private val chapterGate = ReaderLoadGate()

    private val planState = MutableStateFlow(ReaderPagePlan.initial(pageCount))
    private val restoredState = MutableStateFlow(false)
    private val phaseState = MutableStateFlow(ReaderRestorePhase.AWAITING_LAYOUT)

    /** 本次翻页周期内已经入队（或即将入队）的图片下标，0 基。 */
    private val prefetched = mutableSetOf<Int>()

    /** 页码的唯一真源；跳页冻结也活在它里面。 */
    val plan: StateFlow<ReaderPagePlan> = planState.asStateFlow()

    /** 恢复流程是否已完成；为 false 时不允许落盘，否则首帧的第 1 页会覆盖旧进度。 */
    val restored: StateFlow<Boolean> = restoredState.asStateFlow()

    /** 恢复流程所处阶段。 */
    val phase: StateFlow<ReaderRestorePhase> = phaseState.asStateFlow()

    val currentPage: Int get() = planState.value.currentPage

    /** 跳页动画进行中：此时可见页回写被拒绝。 */
    val isJumping: Boolean get() = planState.value.isJumping

    /** 是否可以落盘：恢复流程已完成且本章有页。 */
    val canPersistProgress: Boolean get() = restoredState.value && pageCount > 0

    /**
     * 请求跳到第 [requestedPage] 页，返回 pager/列表要对齐的目标页。
     *
     * 返回 null 表示没有跳页可做：空章，或者计划根本没变。目标页与当前页相同时照旧返回目标页，
     * 屏幕仍执行一次动画、落地后 [onJumpSettled]，与「跳到当前页也要动一下」的既有行为一致。
     */
    fun requestJump(requestedPage: Int): Int? {
        val next = jumpTo(planState.value, requestedPage)
        if (next == planState.value) return null
        planState.value = next
        return next.pendingTarget
    }

    /** 跳页动画落地；只有它仍是当前目标时才解冻回写，旧动画结束不会清掉新目标。 */
    fun onJumpSettled(target: Int) {
        planState.value = settleJump(planState.value, target)
    }

    /**
     * 可见下标变化：回写页码并给出这次该入队的图片下标（0 基，已按账本去重）。
     *
     * 跳页动画进行中页码不动，但预取按目标页推进，动画落地前邻页已经就位。
     */
    fun onVisibleIndex(firstVisibleIndex: Int, ahead: Int): List<Int> {
        val next = advancePrefetch(planState.value, firstVisibleIndex, prefetched, ahead = ahead)
        planState.value = next.plan
        prefetched += next.pages
        return next.pages
    }

    /**
     * 作废预取账本：换章、页宽变化、切换纵横或换质量档后，按旧尺寸解过的页要重新发一次，
     * 否则新尺寸永远等不到解码。
     */
    fun resetPrefetchLedger() {
        prefetched.clear()
    }

    /** 首帧布局落定（纵横模式与页宽已确定），放开恢复流程。 */
    fun onLayoutReady() {
        if (phaseState.value == ReaderRestorePhase.AWAITING_LAYOUT) {
            phaseState.value = ReaderRestorePhase.RESTORING
        }
    }

    /**
     * 恢复上次读到的页码，返回本次恢复的结论。
     *
     * 顺序由本方法保证：先调用 [readSaved] 读出旧进度，再打开写盘闸门，最后结算要跳到的页码。
     * 读旧值这一刻闸门必须还关着，否则首帧的第 1 页会先把旧进度写掉。
     *
     * 返回 null 表示这次调用不该做事：首帧布局还没落定，或者本会话已经恢复过。
     *
     * @param readSaved 读取旧进度；在闸门打开之前被调用
     * @param startPage 详情页预览点进来的起始页，优先于历史进度
     */
    suspend fun restore(readSaved: suspend () -> ReaderPageProgress?, startPage: Int?): ReaderRestoreOutcome? {
        if (phaseState.value != ReaderRestorePhase.RESTORING) return null
        val saved = readSaved()
        restoredState.value = true
        phaseState.value = ReaderRestorePhase.SETTLED
        return ReaderRestoreOutcome(saved = saved, targetPage = resolveInitialPage(startPage, saved, pageCount))
    }

    /** 要落盘的进度；闸门未开或本章没有页时返回 null。 */
    fun progressToSave(): ReaderPageProgress? {
        if (!canPersistProgress) return null
        return ReaderPageProgress(page = currentPage, total = pageCount)
    }

    /** 开始一次章节加载，返回本次代际。 */
    fun beginChapterLoad(): Int = chapterGate.start()

    /** 这次章节加载是否仍是最新一次；旧响应直接丢弃，不覆盖新状态。 */
    fun isLatestChapterLoad(token: Int): Boolean = chapterGate.isLatest(token)

    companion object {
        /** 小说屏只需要章节代际与写盘闸门：没有页码计划，也没有预取。 */
        fun chapterOnly(): ReaderSession = ReaderSession(pageCount = 0)
    }
}
