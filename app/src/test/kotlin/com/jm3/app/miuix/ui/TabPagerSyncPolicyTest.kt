// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabPagerSyncPolicyTest {

    @Test
    fun `页数为零或负数时安全归零且不产出同步目标`() {
        assertEquals(0, TabPagerSyncPolicy.clampTabIndex(2, 0))
        assertEquals(0, TabPagerSyncPolicy.clampTabIndex(-1, -3))
        assertNull(TabPagerSyncPolicy.resolveSettledTabSync(0, 0, 0))
        assertNull(TabPagerSyncPolicy.resolveTargetPageForTabChange(1, 0, 0, 0))
    }

    @Test
    fun `越界 Tab 下标在合法页数内钳制`() {
        assertEquals(0, TabPagerSyncPolicy.clampTabIndex(-2, 4))
        assertEquals(3, TabPagerSyncPolicy.clampTabIndex(9, 4))
        assertEquals(2, TabPagerSyncPolicy.clampTabIndex(2, 4))
    }

    @Test
    fun `Pager 滑动停稳到不同页时返回新 Tab 下标，同页或越界不重复触发`() {
        assertEquals(2, TabPagerSyncPolicy.resolveSettledTabSync(settledPage = 2, currentTab = 0, pageCount = 4))
        assertNull(TabPagerSyncPolicy.resolveSettledTabSync(settledPage = 2, currentTab = 2, pageCount = 4))
        assertNull(TabPagerSyncPolicy.resolveSettledTabSync(settledPage = -1, currentTab = 0, pageCount = 4))
        assertNull(TabPagerSyncPolicy.resolveSettledTabSync(settledPage = 4, currentTab = 0, pageCount = 4))
    }

    @Test
    fun `点击切换 Tab 时解析需要滚动的目标页并忽略已对齐状态`() {
        // 已停稳在当前页且无偏移，不应重复触发滚动动画
        assertNull(
            TabPagerSyncPolicy.resolveTargetPageForTabChange(
                selectedTab = 1,
                settledPage = 1,
                currentPage = 1,
                pageCount = 3,
                hasPageOffset = false,
            ),
        )
        // 目标与当前页不同 -> 滚动到目标页（带边界钳制）
        assertEquals(
            2,
            TabPagerSyncPolicy.resolveTargetPageForTabChange(
                selectedTab = 5,
                settledPage = 0,
                currentPage = 0,
                pageCount = 3,
            ),
        )
        // 半途打断退回原页（settled 相同但 currentPage 或偏移未归位）仍需归位
        assertEquals(
            0,
            TabPagerSyncPolicy.resolveTargetPageForTabChange(
                selectedTab = 0,
                settledPage = 0,
                currentPage = 1,
                pageCount = 3,
            ),
        )
        assertEquals(
            0,
            TabPagerSyncPolicy.resolveTargetPageForTabChange(
                selectedTab = 0,
                settledPage = 0,
                currentPage = 0,
                pageCount = 3,
                hasPageOffset = true,
            ),
        )
    }
}
