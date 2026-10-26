package com.jm3.core

import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPagesTest {

    private fun result(id: String) = SearchResult(
        id = id,
        title = "标题$id",
        authors = listOf("作者$id"),
        tags = listOf("标签$id"),
        coverUrl = "https://cdn.example/media/albums/${id}_3x4.jpg",
    )

    @Test
    fun 加载更多_拼接下一页并按id去重() {
        val first = SearchPage(currentPage = 1, totalPages = 3, items = listOf(result("1"), result("2")))
        val next = SearchPage(currentPage = 2, totalPages = 3, items = listOf(result("2"), result("3")))

        val merged = first.mergedWith(next)

        assertEquals(listOf("1", "2", "3"), merged.items.map { it.id })
        assertEquals(2, merged.currentPage)
        assertEquals(3, merged.totalPages)
    }

    @Test
    fun 加载更多_下一页为空时保持原有内容() {
        val first = SearchPage(currentPage = 1, totalPages = 3, items = listOf(result("1")))
        val next = SearchPage(currentPage = 2, totalPages = 3, items = emptyList())

        val merged = first.mergedWith(next)

        assertEquals(listOf("1"), merged.items.map { it.id })
        assertEquals(2, merged.currentPage)
    }

    @Test
    fun 加载更多_全部重复时不去重出空列表() {
        val first = SearchPage(currentPage = 1, totalPages = 2, items = listOf(result("1"), result("2")))
        val next = SearchPage(currentPage = 2, totalPages = 2, items = listOf(result("1"), result("2")))

        val merged = first.mergedWith(next)

        assertEquals(listOf("1", "2"), merged.items.map { it.id })
        assertEquals(2, merged.currentPage)
    }
}
