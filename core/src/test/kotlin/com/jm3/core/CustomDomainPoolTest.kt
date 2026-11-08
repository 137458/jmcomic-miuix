package com.jm3.core

import io.github.jukomu.jmcomic.core.net.provider.DomainProbe
import io.github.jukomu.jmcomic.core.net.provider.JmDomainManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomDomainPoolTest {

    private class FakeProbe(private val reachable: Set<String>) : DomainProbe {
        override fun isReachable(domain: String): Boolean = domain in reachable
    }

    @Test
    fun `自定义线路排在缓存池之前`() {
        assertEquals(
            listOf("a.example", "b.example", "c.example"),
            startupDomainPool(listOf("a.example"), listOf("b.example", "c.example")),
        )
    }

    @Test
    fun `跨组重复只保留首次出现的位置`() {
        assertEquals(
            listOf("a.example", "b.example", "c.example"),
            startupDomainPool(listOf("a.example", "b.example"), listOf("b.example", "c.example")),
        )
    }

    @Test
    fun `带协议头与尾斜杠的线路归一化成裸域名`() {
        assertEquals(
            listOf("a.example", "b.example"),
            startupDomainPool(listOf("https://a.example/"), listOf("b.example/")),
        )
    }

    @Test
    fun `两组都为空时返回空表`() {
        assertEquals(emptyList(), startupDomainPool(emptyList(), emptyList()))
    }

    @Test
    fun `探活只保留可达线路`() {
        assertEquals(
            listOf("a.example", "c.example"),
            probeDomainPool(
                listOf("a.example", "b.example", "c.example"),
                FakeProbe(setOf("a.example", "c.example")),
            ),
        )
    }

    @Test
    fun `全部探不通返回空表`() {
        assertEquals(
            emptyList(),
            probeDomainPool(listOf("a.example", "b.example"), FakeProbe(emptySet())),
        )
    }

    @Test
    fun `空候选表不产生探活结果`() {
        assertEquals(
            emptyList(),
            probeDomainPool(emptyList(), FakeProbe(setOf("a.example"))),
        )
    }

    @Test
    fun `先探活再把可达线路整体替换进管理器`() {
        val manager = JmDomainManager(listOf("old.example"))
        val probed = mutableListOf<List<String>>()

        val pool = refreshDomainPool(
            manager = manager,
            custom = listOf("custom.example"),
            fetchOfficial = { listOf("official.example", "dead.example") },
            probe = { candidate ->
                probed += candidate
                candidate.filter { it != "dead.example" }
            },
        )

        assertEquals(listOf(listOf("custom.example", "official.example", "dead.example", "old.example")), probed)
        assertEquals(listOf("custom.example", "official.example", "old.example"), pool)
        assertEquals(pool, manager.domains)
    }

    @Test
    fun `拉表为空时不动现有池也不探活`() {
        val manager = JmDomainManager(listOf("old.example"))

        val pool = refreshDomainPool(
            manager = manager,
            custom = listOf("custom.example"),
            fetchOfficial = { emptyList() },
            probe = { error("没有候选池，不应探活") },
        )

        assertNull(pool)
        assertEquals(listOf("old.example"), manager.domains)
    }

    @Test
    fun `全部探不通时不动现有池`() {
        val manager = JmDomainManager(listOf("old.example"))

        val pool = refreshDomainPool(
            manager = manager,
            custom = listOf("custom.example"),
            fetchOfficial = { listOf("dead.example") },
            probe = { emptyList() },
        )

        assertNull(pool)
        assertEquals(listOf("old.example"), manager.domains)
    }
}
