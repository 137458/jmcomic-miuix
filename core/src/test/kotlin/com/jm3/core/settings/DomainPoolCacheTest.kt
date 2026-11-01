package com.jm3.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainPoolCacheTest {

    @Test
    fun `未写入时为空表`() {
        assertEquals(emptyList(), InMemoryDomainPoolCache().read())
    }

    @Test
    fun `写入后读回归一化后的线路`() {
        val cache = InMemoryDomainPoolCache()
        cache.write(listOf(" https://a.example/ ", "a.example", "b.example/"))
        assertEquals(listOf("a.example", "b.example"), cache.read())
    }
}
