package com.jm3.app.miuix.ui

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.model.SearchPage
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 网关结局到列表状态的翻译：首页与浏览页共用这一份，测试与调用方穿过同一个 seam。
 *
 * 覆盖两条判定：列表主数据（失败与失效都落列表错误态，文案统一）、可降级区块（失败与失效都退化为兜底值）。
 */
class ListOutcomeTest {

    private fun page(current: Int, total: Int) = SearchPage(current, total, emptyList())

    @Test
    fun 成功结局落内容态并带上服务端页码() {
        val state = SessionOutcome.Ok(page(2, 7)).asListState("加载失败")

        val loaded = state as? ListState.Loaded ?: error("期望内容态，实际 $state")
        assertEquals(2, loaded.feed.page)
        assertEquals(7, loaded.feed.pageCount)
        assertTrue(loaded.feed.hasMore)
    }

    @Test
    fun 失败结局落列表错误态并保留原始消息() {
        val state = SessionOutcome.Failed("网络错误").asListState("加载失败")

        assertEquals(ListState.Failed("网络错误"), state)
    }

    @Test
    fun 失败消息为空时回落到该屏默认文案() {
        val state = SessionOutcome.Failed(null).asListState("加载失败")

        assertEquals(ListState.Failed("加载失败"), state)
    }

    @Test
    fun 会话失效落错误态且文案是登录提示() {
        val state = SessionOutcome.Expired.asListState("加载失败")

        assertEquals(ListState.Failed(LOGIN_EXPIRED_MESSAGE), state)
    }

    @Test
    fun 可降级区块成功时取原值() {
        assertEquals(listOf("标签"), SessionOutcome.Ok(listOf("标签")).orDegrade(emptyList()))
    }

    @Test
    fun 可降级区块失败或失效时取兜底值() {
        assertEquals(emptyList<String>(), SessionOutcome.Failed("网络错误").orDegrade(emptyList()))
        assertEquals(emptyList<String>(), SessionOutcome.Expired.orDegrade(emptyList()))
    }
}
