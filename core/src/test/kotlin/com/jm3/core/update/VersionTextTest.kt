package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTextTest {

    @Test
    fun 归一去掉首尾空白与v前缀() {
        assertEquals("1.2.0", VersionText.normalized(" v1.2.0 "))
        assertEquals("1.2.0", VersionText.normalized("V1.2.0"))
        assertEquals("1.2.0", VersionText.normalized("1.2.0"))
    }

    @Test
    fun 归一丢掉构建元数据但保留预发布段() {
        assertEquals("1.2.0", VersionText.normalized("v1.2.0+build.7"))
        assertEquals("1.2.0-beta.1", VersionText.normalized("1.2.0-beta.1+sha.5"))
        assertEquals("", VersionText.normalized(null))
        assertEquals("", VersionText.normalized("   "))
    }

    @Test
    fun 解析把末尾零段折掉() {
        assertEquals(listOf(1L, 2L), VersionText.parse("1.2").core)
        assertEquals(listOf(1L, 2L), VersionText.parse("1.2.0").core)
        assertEquals(listOf(1L, 2L), VersionText.parse("v1.2.0.0").core)
        assertEquals(emptyList<Long>(), VersionText.parse("abc").core)
        assertEquals(emptyList<Long>(), VersionText.parse("").core)
    }

    @Test
    fun 解析拆出预发布段() {
        assertEquals(listOf("beta", "1"), VersionText.parse("1.2.0-beta.1").preRelease)
        assertNull(VersionText.parse("v1.0.0+2").preRelease)
        assertNull(VersionText.parse("1.2.0").preRelease)
    }

    @Test
    fun 同一版本的不同写法互相等价() {
        assertTrue(VersionText.sameVersion("v1.2.0", "1.2.0"))
        assertTrue(VersionText.sameVersion("1.2", "1.2.0"))
        assertTrue(VersionText.sameVersion("1.2.0.0", "1.2.0"))
        assertTrue(VersionText.sameVersion("1.2.0+build.7", "1.2.0"))
        assertTrue(VersionText.sameVersion("  ", ""))
        assertTrue(VersionText.sameVersion(null, ""))
    }

    @Test
    fun 不同版本与预发布不算等价() {
        assertFalse(VersionText.sameVersion("1.2.0-beta.1", "1.2.0"))
        assertFalse(VersionText.sameVersion("1.2.0", "1.2.1"))
        assertFalse(VersionText.sameVersion("1.0.0-beta.1", "1.0.0-beta.2"))
    }

    @Test
    fun 等价判定与比较器零值一致() {
        val versions = listOf(
            "v1.2.0", "1.2", "1.2.0", "1.2.0.0", "1.2.0+build.7", "1.2.0-beta.1", "1.2.1", "", "  ",
        )
        for (a in versions) {
            for (b in versions) {
                assertEquals(
                    VersionCompare.compare(a, b) == 0,
                    VersionText.sameVersion(a, b),
                    "a=$a b=$b",
                )
            }
        }
    }
    @Test
    fun 展示版本与忽略记录共用归一() {
        val release = ReleaseInfo(tagName = "v1.0.0+build.7", name = "", notes = "", htmlUrl = "", publishedAt = "")
        assertEquals("1.0.0", release.version)
        assertTrue(VersionText.sameVersion(release.version, "1.0.0"))
    }
}
