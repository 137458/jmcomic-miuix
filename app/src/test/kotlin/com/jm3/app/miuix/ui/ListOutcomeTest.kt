package com.jm3.app.miuix.ui

import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 可降级区块的取值：侧栏标签、随便看看、连载更新、分类树这些增强内容，失败与会话失效一律
 * 退化成兜底值，绝不因此顶掉主列表。
 */
class ListOutcomeTest {

    @Test
    fun 可降级区块成功时取原值() {
        assertEquals("内容", SessionOutcome.Ok("内容").orDegrade("兜底"))
    }

    @Test
    fun 可降级区块失败或失效时取兜底值() {
        assertEquals("兜底", SessionOutcome.Failed("网络不可用").orDegrade("兜底"))
        assertEquals("兜底", SessionOutcome.Failed(null).orDegrade("兜底"))
        assertEquals("兜底", SessionOutcome.Expired.orDegrade("兜底"))
    }
}
