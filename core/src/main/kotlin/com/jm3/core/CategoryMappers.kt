package com.jm3.core

import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.CategoryNode
import com.jm3.core.model.CategoryTree
import io.github.jukomu.jmcomic.api.model.JmCategoryList
import io.github.jukomu.jmcomic.api.model.JmCategoryListItem

/** 分类条目：总数可能为字符串；缺失按 0。子分类同样递归成节点，供「更多分类」弹窗使用。 */
fun JmCategoryListItem.toCategoryNode(): CategoryNode = CategoryNode(
    id = id().orEmpty(),
    name = name().orEmpty(),
    slug = slug().orEmpty(),
    totalAlbums = totalAlbums()?.toIntOrNull() ?: 0,
    children = subCategories().orEmpty().map { sub ->
        CategoryNode(
            id = sub.cid().orEmpty(),
            name = sub.name().orEmpty(),
            slug = sub.slug().orEmpty(),
        )
    },
)

/** 分类树：一级分类 + 标签区块。 */
fun JmCategoryList.toCategoryTree(): CategoryTree = CategoryTree(
    categories = categories().orEmpty().map { it.toCategoryNode() },
    blocks = blocks().orEmpty().map { block ->
        CategoryBlock(
            title = block.title().orEmpty(),
            tags = block.content().orEmpty(),
        )
    },
)
