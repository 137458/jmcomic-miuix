package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.CategoryBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class CouiBrowseFilterTest {

    private val sampleBlocks = listOf(
        CategoryBlock(title = "主題A", tags = listOf("全彩", "纯爱", "BL", "长篇")),
        CategoryBlock(title = "题材", tags = listOf("校园", "奇幻", "Webtoon")),
        CategoryBlock(title = "空分区", tags = emptyList()),
    )

    @Test
    fun 空查询与空白查询仅保留非空全量分区() {
        val expected = sampleBlocks.take(2)
        assertEquals(expected, couiFilterCategoryBlocks(sampleBlocks, ""))
        assertEquals(expected, couiFilterCategoryBlocks(sampleBlocks, "   \t "))
    }

    @Test
    fun 关键词按标签大小写不敏感筛选并剔除空分块() {
        val result = couiFilterCategoryBlocks(sampleBlocks, "  bl ")
        assertEquals(
            listOf(CategoryBlock(title = "主題A", tags = listOf("BL"))),
            result,
        )
    }

    @Test
    fun 命中分块标题时保留该分块全部标签且兼顾跨块标签命中() {
        val result = couiFilterCategoryBlocks(sampleBlocks, "题材")
        assertEquals(
            listOf(CategoryBlock(title = "题材", tags = listOf("校园", "奇幻", "Webtoon"))),
            result,
        )
    }

    @Test
    fun 无任何匹配关键词时返回空列表() {
        assertEquals(emptyList(), couiFilterCategoryBlocks(sampleBlocks, "不存在的标签"))
    }
}
