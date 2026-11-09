package com.jm3.app.miuix.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 进度节流的业务契约：没到上报间隔不返回速度，到点按 7:3 的指数滑动平均平滑速度。
 *
 * 用可手动推进的假时钟而不是真实时间：android.os.SystemClock 在 JVM 单测里是返回默认值的桩，
 * 恒为 0，节流分支永远进不去、速度恒为 0，只能注入时钟。
 * 每组数据都从 0 或指定起点重新建对象，用例之间不共享时钟状态。
 */
class UpdateProgressThrottleTest {

    /** 可手动推进的假时钟 */
    private var now = 0L

    private fun throttle(intervalMs: Long = 400L) = UpdateProgressThrottle({ now }, intervalMs)

    @Test
    fun `未到上报间隔时不返回速度`() {
        // 第一组：间隔 400ms，差 1ms 仍不上报
        now = 0L
        val quarterSecond = throttle(intervalMs = 400L)
        now = 399L
        assertNull(quarterSecond.onBytes(399L), "距上次上报 399ms，未到 400ms 不应上报")
        assertEquals(0L, quarterSecond.speedBytesPerSec, "没上报过时速度保持 0")

        // 第二组：间隔换成 500ms 且起点不在 0，验证判据是"距离上次上报的间隔"而不是绝对时间或写死的 400
        now = 1_000L
        val halfSecond = throttle(intervalMs = 500L)
        now = 1_499L
        assertNull(halfSecond.onBytes(1_000L), "距上次上报 499ms，未到 500ms 不应上报")
        now = 1_500L
        assertEquals(1_200L, halfSecond.onBytes(2_000L), "到 500ms 上报：2000B / 0.5s = 4000B/s，首次平滑取 3/10 得 1200")
    }

    @Test
    fun `到点上报时按七比三的指数滑动平均更新速度`() {
        // 第一组：间隔 400ms，瞬时速度 1000 -> 2000 -> 1000 B/s 被平滑成 300 -> 810 -> 867
        now = 0L
        val quarterSecond = throttle(intervalMs = 400L)
        now = 400L
        assertEquals(300L, quarterSecond.onBytes(400L), "首次上报：400B / 0.4s = 1000B/s，按 3/10 折成 300")
        now = 2_000L
        assertEquals(810L, quarterSecond.onBytes(3_600L), "第二次：3200B / 1.6s = 2000B/s，与 300 按 7:3 平滑成 810")
        now = 2_400L
        assertEquals(
            867L,
            quarterSecond.onBytes(4_000L),
            "第三次：400B / 0.4s = 1000B/s，与 810 按 7:3 平滑成 867",
        )
        assertEquals(867L, quarterSecond.speedBytesPerSec, "对外暴露的平滑速度应与本次上报值一致")

        // 第二组：间隔 500ms，不同数值验证公式不是针对第一组特化的
        now = 1_000L
        val halfSecond = throttle(intervalMs = 500L)
        now = 1_500L
        assertEquals(600L, halfSecond.onBytes(1_000L), "首次上报：1000B / 0.5s = 2000B/s，按 3/10 折成 600")
        now = 2_500L
        assertEquals(1_920L, halfSecond.onBytes(6_000L), "第二次：5000B / 1s = 5000B/s，与 600 按 7:3 平滑成 1920")
    }
}
