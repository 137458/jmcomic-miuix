package com.jm3.app.miuix.ui

import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderLoadFailureTest {

    @Test
    fun 成功结局不产生失败态() {
        // 成功分支由调用方继续走内容，不该在这里被翻译成失败
        assertNull(readerLoadFailure(SessionOutcome.Ok(Unit)))
    }

    @Test
    fun 失效结局进入登录引导且不可重试() {
        val failed = readerLoadFailure(SessionOutcome.Expired)

        assertTrue(failed!!.sessionExpired)
        assertEquals(SessionOutcome.Expired.message, failed.message)
    }

    @Test
    fun 普通失败给出归类文案且保留重试() {
        val failed = readerLoadFailure(SessionOutcome.Failed("boom"))

        assertFalse(failed!!.sessionExpired)
        assertEquals("章节加载失败", failed.message)
    }

    @Test
    fun 普通失败没有原因时回落到章节加载失败() {
        val failed = readerLoadFailure(SessionOutcome.Failed(null))

        assertEquals("章节加载失败", failed!!.message)
    }
}
