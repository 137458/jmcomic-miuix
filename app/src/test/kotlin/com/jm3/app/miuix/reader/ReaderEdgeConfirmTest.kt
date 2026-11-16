package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.EDGE_CONFIRM_MOVE_PX
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 边缘空拖的松手复核：等惯性起步后内容位移小于 [EDGE_CONFIRM_MOVE_PX] 才算空拖成立。
 *
 * 位移一律按内容坐标系取值：放大后内容向右平移为正、向左为负，判定只看绝对位移。
 */
class ReaderEdgeConfirmTest {

    @Test
    fun 内容停在边界时复核通过() {
        assertTrue(edgeDragStillPinned(offsetAtRelease = -120f, offsetNow = -120f))
        assertTrue(edgeDragStillPinned(offsetAtRelease = -120f, offsetNow = -113f))
        assertTrue(edgeDragStillPinned(offsetAtRelease = 40f, offsetNow = 47.5f))
    }

    @Test
    fun 惯性还在带走内容时复核失败() {
        assertFalse(edgeDragStillPinned(offsetAtRelease = -120f, offsetNow = -111f))
        assertFalse(edgeDragStillPinned(offsetAtRelease = -120f, offsetNow = -60f))
        assertFalse(edgeDragStillPinned(offsetAtRelease = 0f, offsetNow = 30f))
    }

    @Test
    fun 复核阈值是开区间() {
        // 恰好等于阈值说明内容已经移动了这么多，不再算「仍在边界」
        assertFalse(edgeDragStillPinned(offsetAtRelease = 16f, offsetNow = 16f + EDGE_CONFIRM_MOVE_PX))
        assertTrue(edgeDragStillPinned(offsetAtRelease = 16f, offsetNow = 16f + EDGE_CONFIRM_MOVE_PX - 0.5f))
    }
}
