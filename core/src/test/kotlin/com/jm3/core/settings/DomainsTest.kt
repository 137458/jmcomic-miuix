package com.jm3.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainsTest {

    @Test
    fun 去除空白项与首尾空白() {
        assertEquals(listOf("a.com", "b.com"), Domains.normalize(listOf(" a.com ", "  ", "b.com", "")))
    }

    @Test
    fun 去除协议头与尾部斜杠() {
        assertEquals(
            listOf("www.example.com", "cdn.example.com"),
            Domains.normalize(listOf("https://www.example.com/", "http://cdn.example.com")),
        )
    }

    @Test
    fun 同项去重且保留首次出现顺序() {
        assertEquals(
            listOf("a.com", "b.com"),
            Domains.normalize(listOf("a.com", "https://a.com/", "b.com")),
        )
    }

    @Test
    fun 全部非法输入返回空表() {
        assertEquals(emptyList(), Domains.normalize(listOf("  ", "https://", "")))
    }
}
