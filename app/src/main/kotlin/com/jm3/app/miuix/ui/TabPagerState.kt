// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * 纯函数策略：协调 Tab 选中下标与 [androidx.compose.foundation.pager.HorizontalPager] 双向同步，
 * 避免“程序滚动 -> 回调改 Tab -> 再次触发滚动”的乒乓震荡，并兜底非法下标与空页数。
 */
object TabPagerSyncPolicy {

    fun clampTabIndex(tabIndex: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return tabIndex.coerceIn(0, pageCount - 1)
    }

    fun resolveSettledTabSync(
        settledPage: Int,
        currentTab: Int,
        pageCount: Int,
    ): Int? {
        if (pageCount <= 0) return null
        if (settledPage !in 0 until pageCount) return null
        return settledPage.takeIf { it != currentTab }
    }

    fun resolveTargetPageForTabChange(
        selectedTab: Int,
        settledPage: Int,
        currentPage: Int,
        pageCount: Int,
        hasPageOffset: Boolean = false,
    ): Int? {
        if (pageCount <= 0) return null
        val target = clampTabIndex(selectedTab, pageCount)
        if (currentPage == target && settledPage == target && !hasPageOffset) {
            return null
        }
        return target
    }
}

/**
 * 记住一个与当前 [selectedTab] 双向同步的 [PagerState]：
 * - 外部切换 Tab 时平滑滚动到目标页（Miuix 侧可用弹簧曲线，COUI 侧用常规动画）；
 * - 用户左右滑动 `HorizontalPager` 停稳后回写 [onTabSelected]，不在拖动半途反复改写源状态。
 */
@Composable
fun rememberSyncedTabPagerState(
    selectedTab: Int,
    pageCount: Int,
    onTabSelected: (Int) -> Unit,
    useMiuixSpring: Boolean = false,
): PagerState {
    val safePageCount = pageCount.coerceAtLeast(0)
    val clampedSelected = TabPagerSyncPolicy.clampTabIndex(selectedTab, safePageCount)
    val pagerState = rememberPagerState(
        initialPage = clampedSelected,
        pageCount = { safePageCount },
    )
    val latestSelectedTab by rememberUpdatedState(clampedSelected)
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    LaunchedEffect(pagerState, safePageCount) {
        if (safePageCount <= 0) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                TabPagerSyncPolicy.resolveSettledTabSync(
                    settledPage = settled,
                    currentTab = latestSelectedTab,
                    pageCount = safePageCount,
                )?.let(latestOnTabSelected)
            }
    }

    LaunchedEffect(clampedSelected, safePageCount, useMiuixSpring) {
        val target = TabPagerSyncPolicy.resolveTargetPageForTabChange(
            selectedTab = clampedSelected,
            settledPage = pagerState.settledPage,
            currentPage = pagerState.currentPage,
            pageCount = safePageCount,
            hasPageOffset = pagerState.currentPageOffsetFraction != 0f,
        ) ?: return@LaunchedEffect
        if (useMiuixSpring) {
            pagerState.springAnimateToPage(target)
        } else {
            pagerState.animateScrollToPage(target)
        }
    }

    return pagerState
}
