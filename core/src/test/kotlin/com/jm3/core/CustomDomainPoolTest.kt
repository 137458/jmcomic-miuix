package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 自定义线路与库内域名池的合并规则：只在缺失时补入，且自定义线路排在池首。 */
class CustomDomainPoolTest {

    @Test
    fun 空池_自定义线路整体入库并保持顺序() {
        val pool = mutableListOf<String>()

        assertTrue(mergeCustomDomains(pool, listOf("a.example", "b.example")))

        assertEquals(listOf("a.example", "b.example"), pool)
    }

    @Test
    fun 池内已有其他线路_只补缺失项且自定义排在前面() {
        val pool = mutableListOf("old.example")

        assertTrue(mergeCustomDomains(pool, listOf("a.example", "b.example")))

        assertEquals(listOf("a.example", "b.example", "old.example"), pool)
    }

    @Test
    fun 自定义线路全部已在池内_不新增也不改顺序() {
        val pool = mutableListOf("a.example", "b.example")

        assertFalse(mergeCustomDomains(pool, listOf("b.example", "a.example")))

        assertEquals(listOf("a.example", "b.example"), pool)
    }

    @Test
    fun 空白项与重复项被丢弃() {
        val pool = mutableListOf<String>()

        assertTrue(mergeCustomDomains(pool, listOf("  a.example  ", "", "   ", "a.example")))

        assertEquals(listOf("a.example"), pool)
    }

    @Test
    fun 空自定义表_视为无变化() {
        val pool = mutableListOf("a.example")

        assertFalse(mergeCustomDomains(pool, emptyList()))

        assertEquals(listOf("a.example"), pool)
    }
@Test
    fun 启动池_自定义在前缓存居中兜底在后() {
        val pool = startupDomainPool(
            listOf("custom.example"),
            listOf("cached.example"),
            listOf("fallback.example"),
        )

        assertEquals(listOf("custom.example", "cached.example", "fallback.example"), pool)
    }

    @Test
    fun 启动池_跨组重复项只保留首次出现() {
        val pool = startupDomainPool(
            listOf("a.example"),
            listOf("b.example", "a.example"),
            listOf("b.example", "c.example"),
        )

        assertEquals(listOf("a.example", "b.example", "c.example"), pool)
    }

    @Test
    fun 启动池_带协议头与尾部斜杠被归一化成裸域名() {
        val pool = startupDomainPool(
            listOf("https://custom.example/"),
            listOf("  cached.example  "),
            listOf("http://fallback.example"),
        )

        assertEquals(listOf("custom.example", "cached.example", "fallback.example"), pool)
    }

    @Test
    fun 启动池_空组不占位() {
        val pool = startupDomainPool(emptyList(), emptyList(), listOf("fallback.example"))

        assertEquals(listOf("fallback.example"), pool)
    }
}
