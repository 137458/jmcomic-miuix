// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 搜索摘要文案：标签搜索要标明是标签，普通搜索把关键词括起来；
 * 结果总数只在服务端确实给出（大于 0）时追加，0、负数与 null 都不能拼出「共 0 个结果」。
 */
class CouiHomeScreenTest {

    @Test
    fun 普通搜索把关键词括起来并追加总数() {
        assertEquals(
            "“魔法少女” · 共 12 个结果",
            searchSummary(query = "魔法少女", byTag = false, total = 12),
        )
    }

    @Test
    fun 标签搜索标明标签并追加总数() {
        assertEquals(
            "标签：同人 · 共 3 个结果",
            searchSummary(query = "同人", byTag = true, total = 3),
        )
    }

    @Test
    fun 总数只有一个时同样显示计数() {
        assertEquals(
            "“NOK” · 共 1 个结果",
            searchSummary(query = "NOK", byTag = false, total = 1),
        )
    }

    @Test
    fun 总数未提供时只留关键词() {
        assertEquals("“魔法少女”", searchSummary(query = "魔法少女", byTag = false, total = null))
        assertEquals("标签：同人", searchSummary(query = "同人", byTag = true, total = null))
    }

    @Test
    fun 总数为零时不留计数() {
        assertEquals("“魔法少女”", searchSummary(query = "魔法少女", byTag = false, total = 0))
        assertEquals("标签：同人", searchSummary(query = "同人", byTag = true, total = 0))
    }

    @Test
    fun 负数总数同样不留计数() {
        assertEquals("“魔法少女”", searchSummary(query = "魔法少女", byTag = false, total = -1))
    }

    @Test
    fun 空关键词仍保留引号对() {
        assertEquals("“”", searchSummary(query = "", byTag = false, total = null))
    }
}
