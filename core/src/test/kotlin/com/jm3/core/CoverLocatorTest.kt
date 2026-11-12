package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 图片域名与封面地址的唯一入口：归一、封面拼接、相对路径拼接三件事都在这里判定。 */
class CoverLocatorTest {

    @Test
    fun 完整地址只留域名() {
        assertEquals(
            "cdn.example.xyz",
            CoverLocator.hostOf("https://cdn.example.xyz/media/albums/123456_3x4.jpg"),
        )
        assertEquals("cdn.example.xyz", CoverLocator.hostOf("http://cdn.example.xyz"))
    }

    @Test
    fun 域名两边留的协议头与尾部斜杠都去掉() {
        assertEquals("www.example.com", CoverLocator.hostOf(" https://www.example.com// "))
        assertEquals("www.example.com", CoverLocator.hostOf("HTTPS://www.example.com:443/api"))
        assertEquals("18comic.vip", CoverLocator.hostOf("  .18comic.vip/  "))
        assertEquals("", CoverLocator.hostOf("..."))
    }

    @Test
    fun 空白输入归一为空域名() {
        assertEquals("", CoverLocator.hostOf(null))
        assertEquals("", CoverLocator.hostOf("   "))
        assertEquals("", CoverLocator.hostOf("https://"))
    }

    @Test
    fun 域名未就绪时封面与相对图片都不给地址() {
        val unready = CoverLocator.of("")
        assertNull(unready.cover(albumId = "42", size = "_3x4"))
        assertNull(unready.image("/media/badges/1.png"))
        assertEquals("", unready.host)
    }

    @Test
    fun 常规域名与尺寸拼成封面地址() {
        assertEquals(
            "https://cdn-msp.example.xyz/media/albums/123456_3x4.jpg",
            CoverLocator.of("cdn-msp.example.xyz").cover(albumId = "123456", size = "_3x4"),
        )
    }

    @Test
    fun 域名自带协议头与路径时封面只取域名() {
        assertEquals(
            "https://cdn.example.com/media/albums/42.jpg",
            CoverLocator.of("https://cdn.example.com/some/path/").cover(albumId = "42"),
        )
    }

    @Test
    fun 空车号不给封面地址() {
        assertNull(CoverLocator.of("cdn.example.com").cover(albumId = ""))
        assertNull(CoverLocator.of("cdn.example.com").cover(albumId = null))
    }

    @Test
    fun 相对图片路径缺前导斜杠时补一个() {
        assertEquals(
            "https://cdn.example.com/media/badges/7.png",
            CoverLocator.of("cdn.example.com").image("media/badges/7.png"),
        )
        assertEquals(
            "https://cdn.example.com/media/badges/7.png",
            CoverLocator.of("cdn.example.com").image("/media/badges/7.png"),
        )
    }

    @Test
    fun 绝对图片地址原样返回() {
        val locator = CoverLocator.of("cdn.example.com")
        assertEquals("https://other.example.com/a.png", locator.image("https://other.example.com/a.png"))
        assertEquals("http://other.example.com/a.png", locator.image("http://other.example.com/a.png"))
        assertEquals(
            "https://other.example.com/a.png",
            CoverLocator.of("").image("https://other.example.com/a.png"),
        )
    }

    @Test
    fun 空白图片路径不给地址() {
        val locator = CoverLocator.of("cdn.example.com")
        assertNull(locator.image(null))
        assertNull(locator.image("  "))
    }
}
