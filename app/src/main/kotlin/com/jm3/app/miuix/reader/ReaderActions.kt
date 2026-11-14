package com.jm3.app.miuix.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import com.jm3.app.miuix.ui.EdgePageTurn
import com.jm3.core.reader.ReaderPageProgress
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.reader.persistReaderProgress
import com.jm3.core.settings.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * 阅读器的翻页与进度落盘：两套皮肤逐字相同的编排收在这里。
 *
 * [pagerPageCount] 与 [spreadActive]、[horizontal] 按值取用：调用方每次重组重建一个实例，
 * 与原先本地的 fun 一样读到当前值；不要缓存实例跨重组复用。
 */
internal class ReaderActions(
    private val session: ReaderSession,
    private val pagerState: PagerState,
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val pageCount: Int,
    private val pagerPageCount: Int,
    private val spreadActive: Boolean,
    private val horizontal: Boolean,
    private val settings: ReaderSettings,
    private val photoId: String,
) {
    /**
     * 边界拖拽翻页的唯一出口：按方向取相邻页，夹进 pager 有效范围后起 Spring 动画。
     * 双页与单页两条编排共用，避免一处夹取、另一处漏夹取而落到不存在的页。
     * 目标页在松手时定下（与旧编排一致），惯性复核通过后才真的翻。
     */
    fun turnPage(turn: EdgePageTurn, stillPinned: suspend () -> Boolean) {
        val target = pagerState.currentPage + if (turn == EdgePageTurn.Next) 1 else -1
        scope.launch {
            if (!stillPinned()) return@launch
            pagerState.springAnimateToPage(clampPagerTarget(target, pagerPageCount))
        }
    }

    /** 跳页：先让计划进入“跳页中”，再驱动 pager 或列表；落地后才恢复可见页回写。 */
    fun goToPage(page: Int) {
        // 双页下把目标换算成所在跨页的锚点：进度、页码与跳页都用同一个单页坐标，
        // 用户输入第 5 页时落到含 4、5 页的那一屏，不会因为并排而错开一页。
        val canonical = if (spreadActive) {
            ReaderSpreadPolicy.anchorPageForPage(page, pageCount)
        } else {
            page
        }
        // 冻结与解冻都交给 session：目标页在动画前定下，落地后才放开可见页回写
        val target = session.requestJump(canonical) ?: return
        if (horizontal) {
            val pagerTarget = if (spreadActive) {
                ReaderSpreadPolicy.spreadIndexForPage(target, pageCount)
            } else {
                target - 1
            }
            scope.launch {
                try {
                    pagerState.springAnimateToPage(clampPagerTarget(pagerTarget, pagerPageCount))
                } finally {
                    session.onJumpSettled(target)
                }
            }
        } else {
            scope.launch {
                try {
                    listState.scrollToItem(target - 1)
                } finally {
                    session.onJumpSettled(target)
                }
            }
        }
    }

    /** 记进度：已读到末页说明本章读完，清键；否则写键。规则见 [persistReaderProgress]。 */
    suspend fun persistProgress(progress: ReaderPageProgress) {
        persistReaderProgress(settings, photoId, progress)
    }
}
