package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 切换佩戴的响应判定：HTTP 200 不代表业务成功，必须看 status。 */
class ClaimTaskFailureTest {

    @Test
    fun `status ok means success`() {
        assertNull(claimTaskFailure(mapOf("status" to "ok", "msg" to "佩戴成功")))
    }

    @Test
    fun `errorMsg wins over msg`() {
        assertEquals(
            "没有这枚勋章",
            claimTaskFailure(mapOf("status" to "error", "errorMsg" to "没有这枚勋章", "msg" to "失败")),
        )
    }

    @Test
    fun `falls back to msg when errorMsg is absent`() {
        assertEquals(
            "请先登录",
            claimTaskFailure(mapOf("status" to "error", "msg" to "请先登录")),
        )
    }

    @Test
    fun `blank or missing payload never reports success`() {
        assertEquals("切换失败", claimTaskFailure(emptyMap<String, String>()))
        assertEquals("切换失败", claimTaskFailure(mapOf("status" to "", "msg" to "  ")))
        assertEquals("切换失败", claimTaskFailure(mapOf("status" to "error")))
    }
}
