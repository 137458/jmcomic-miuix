package com.jm3.core

import io.github.jukomu.jmcomic.api.model.JmCategoryBlock
import io.github.jukomu.jmcomic.api.model.JmCategoryList
import io.github.jukomu.jmcomic.api.model.JmCategoryListItem
import io.github.jukomu.jmcomic.api.model.JmSubCategoryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryMappersTest {

    @Test
    fun `toCategoryNode 在 subCategories 为 null 时安全返回空子节点`() {
        val item = JmCategoryListItem("1", "同人", "doujin", "cat", "123", null)
        val node = item.toCategoryNode()

        assertEquals("1", node.id)
        assertEquals("同人", node.name)
        assertEquals("doujin", node.slug)
        assertEquals(123, node.totalAlbums)
        assertTrue(node.children.isEmpty())
    }

    @Test
    fun `toCategoryTree 在 categories 和 blocks 为 null 时安全返回空列表`() {
        val tree = JmCategoryList(null, null).toCategoryTree()

        assertTrue(tree.categories.isEmpty())
        assertTrue(tree.blocks.isEmpty())
    }

    @Test
    fun `toCategoryTree 正常映射一级分类与标签区块`() {
        val item = JmCategoryListItem(
            "1",
            "同人",
            "doujin",
            "cat",
            "bad-num",
            listOf(JmSubCategoryItem("11", "中文", "chinese")),
        )
        val block = JmCategoryBlock("主题", listOf("纯爱", "全彩"))
        val tree = JmCategoryList(listOf(item), listOf(block)).toCategoryTree()

        assertEquals(1, tree.categories.size)
        assertEquals(0, tree.categories[0].totalAlbums)
        assertEquals("11", tree.categories[0].children.single().id)
        assertEquals("主题", tree.blocks.single().title)
        assertEquals(listOf("纯爱", "全彩"), tree.blocks.single().tags)
    }
}
