package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionCookiesTest {

    private fun cookie(name: String, value: String, domain: String, path: String = "/") =
        CookieSnapshot(name = name, value = value, domain = domain, path = path)

    @Test
    fun 会话Cookie_广播_每份Cookie覆盖全部线路域名() {
        val origin = listOf(cookie("AVS", "s1", "a.example"), cookie("sl", "g", "a.example"))

        val got = origin.broadcastTo(listOf("a.example", "b.example", "c.example"))

        assertEquals(
            listOf(
                "AVS" to "a.example",
                "sl" to "a.example",
                "AVS" to "b.example",
                "sl" to "b.example",
                "AVS" to "c.example",
                "sl" to "c.example",
            ),
            got.map { it.name to it.domain },
        )
        assertEquals("s1", got.first { it.name == "AVS" && it.domain == "c.example" }.value)
    }

    @Test
    fun 会话Cookie_广播_线路为空时保持原样() {
        val origin = listOf(cookie("AVS", "s1", "a.example"))

        val got = origin.broadcastTo(emptyList())

        assertEquals(origin, got)
    }

    @Test
    fun 会话Cookie_广播_跳过空域名且不重复展开() {
        val origin = listOf(cookie("AVS", "s1", "a.example"))

        val got = origin.broadcastTo(listOf("a.example", "  ", "a.example"))

        assertEquals(listOf("AVS" to "a.example"), got.map { it.name to it.domain })
    }

    @Test
    fun 会话Cookie_广播_保留原Cookie的路径() {
        val origin = listOf(cookie("AVS", "s1", "a.example", path = "/api"))

        val got = origin.broadcastTo(listOf("b.example"))

        assertEquals(listOf("a.example" to "/api", "b.example" to "/api"), got.map { it.domain to it.path })
    }
}
