package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCompareTest {

    @Test
    fun 补丁位更高的远端判为有更新() {
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0", "1.0.1"))
        assertEquals(VersionRelation.OLDER, VersionCompare.relation("1.0.1", "1.0.0"))
    }

    @Test
    fun 次版本与主版本按数值比较() {
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0", "1.1.0"))
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0", "2.0.0"))
        // 数值比较而非字典序：1.10.0 比 1.9.0 新
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.9.0", "1.10.0"))
        assertEquals(VersionRelation.OLDER, VersionCompare.relation("2.0.0", "1.9.9"))
    }

    @Test
    fun 同版本不同写法判为相同() {
        assertEquals(VersionRelation.SAME, VersionCompare.relation("1.0.0", "1.0.0"))
        // 缺段补 0：1.0 == 1.0.0
        assertEquals(VersionRelation.SAME, VersionCompare.relation("1.0", "1.0.0"))
        assertEquals(VersionRelation.SAME, VersionCompare.relation("1.0.0.0", "1.0.0"))
        assertEquals(0, VersionCompare.compare("1.0.0", "1.0.0.0"))
    }

    @Test
    fun v前缀与首尾空白不影响比较() {
        assertEquals(VersionRelation.SAME, VersionCompare.relation("1.0.0", "v1.0.0"))
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0", "v1.1.0"))
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0", " V1.1.0 "))
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("v1.0.0", "1.0.1"))
    }

    @Test
    fun 预发布版本低于同号正式版() {
        assertEquals(VersionRelation.OLDER, VersionCompare.relation("1.0.0", "1.0.0-beta.1"))
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0-beta.1", "1.0.0"))
        // 预发布标识符里的数字按数值比较：beta.10 比 beta.2 新
        assertEquals(VersionRelation.OLDER, VersionCompare.relation("1.0.0-beta.10", "1.0.0-beta.2"))
        assertEquals(VersionRelation.OLDER, VersionCompare.relation("1.0.0-beta", "1.0.0-alpha"))
        assertEquals(VersionRelation.SAME, VersionCompare.relation("1.0.0-beta.1", "v1.0.0-beta.1"))
        // 标识符前缀相同则短者更小：1.0.0-beta < 1.0.0-beta.1
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("1.0.0-beta", "1.0.0-beta.1"))
    }

    @Test
    fun 构建元数据不参与比较() {
        assertEquals(VersionRelation.SAME, VersionCompare.relation("1.0.0+build.7", "1.0.0"))
        assertEquals(VersionRelation.SAME, VersionCompare.relation("v1.0.0+2", "1.0.0+1"))
    }

    @Test
    fun 非数字内容按零处理且不抛异常() {
        assertEquals(VersionRelation.SAME, VersionCompare.relation("abc", "0.0.0"))
        assertEquals(VersionRelation.NEWER, VersionCompare.relation("", "1.0.0"))
        assertEquals(VersionRelation.SAME, VersionCompare.relation("v", ""))
    }
}
