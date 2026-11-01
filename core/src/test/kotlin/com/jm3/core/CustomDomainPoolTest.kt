package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CustomDomainPoolTest {

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
}
