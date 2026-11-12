// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 信箱滑动行动作触发判定契约：
 * - 越过正向半程（>= 0.5 * reachPx）触发起始侧动作（已读/未读）；
 * - 越过负向半程（<= -0.5 * reachPx）触发末尾侧动作（展开/收起）；
 * - 未达半程或 reachPx <= 0 时不触发任何动作。
 */
class CouiNotificationsSwipeTest {

    @Test
    fun 超过半程触发对应侧动作且恰好半程也算触发() {
        assertEquals(CouiSwipeActionTrigger.START, resolveCouiSwipeAction(offsetPx = 48f, reachPx = 96f))
        assertEquals(CouiSwipeActionTrigger.START, resolveCouiSwipeAction(offsetPx = 96f, reachPx = 96f))
        assertEquals(CouiSwipeActionTrigger.END, resolveCouiSwipeAction(offsetPx = -48f, reachPx = 96f))
        assertEquals(CouiSwipeActionTrigger.END, resolveCouiSwipeAction(offsetPx = -80f, reachPx = 96f))
    }

    @Test
    fun 未达半程或无效行程不触发动作() {
        assertEquals(CouiSwipeActionTrigger.NONE, resolveCouiSwipeAction(offsetPx = 47.9f, reachPx = 96f))
        assertEquals(CouiSwipeActionTrigger.NONE, resolveCouiSwipeAction(offsetPx = -47.9f, reachPx = 96f))
        assertEquals(CouiSwipeActionTrigger.NONE, resolveCouiSwipeAction(offsetPx = 0f, reachPx = 96f))
        assertEquals(CouiSwipeActionTrigger.NONE, resolveCouiSwipeAction(offsetPx = 50f, reachPx = 0f))
        assertEquals(CouiSwipeActionTrigger.NONE, resolveCouiSwipeAction(offsetPx = 50f, reachPx = -10f))
    }
}
