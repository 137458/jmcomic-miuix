package com.jm3.core

import io.github.jukomu.jmcomic.api.exception.ParseResponseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SerializationPageTest {

    @Test
    fun `解析 list 与数值 total 并按本页条数推算总页数`() {
        val itemsJson = (1..30).joinToString(",") { i ->
            val subTitle = if (i == 1) "null" else "\"漢化\""
            """{"id":"${1000 + i}","name":"作品$i","author":"作者$i","category":{"id":"5","title":"韓漫"},"category_sub":{"id":"19","title":$subTitle}}"""
        }
        val json = """{"list":[$itemsJson],"total":238}"""

        val page = SerializationParser.parse(json, page = 1, covers = CoverResolver.of("cdn.example"))

        assertEquals(1, page.currentPage)
        assertEquals(238, page.totalItems)
        assertEquals(8, page.totalPages)
        assertEquals(30, page.items.size)
        val first = page.items.first()
        assertEquals("1001", first.id)
        assertEquals("作品1", first.title)
        assertEquals(listOf("作者1"), first.authors)
        assertEquals("韓漫", first.category)
        assertEquals("https://cdn.example/media/albums/1001_3x4.jpg", first.coverUrl)
    }

    @Test
    fun `主分类缺失且子分类标题为 null 时 category 为 null，有子分类时回退子分类`() {
        val json = """{
            "list": [
                {"id":"2001","name":"无分类","author":["A","B"],"category":{"id":"0","title":""},"category_sub":{"id":"0","title":null}},
                {"id":"2002","name":"仅子分类","author":"C","category":null,"category_sub":{"id":"12","title":"青年漫"}}
            ],
            "total": "79"
        }"""

        val page = SerializationParser.parse(json, page = 1)

        assertEquals(79, page.totalItems)
        assertNull(page.items[0].category)
        assertEquals(listOf("A", "B"), page.items[0].authors)
        assertEquals("青年漫", page.items[1].category)
    }

    @Test
    fun `list 为空且 total 为 0 时返回空页与 0 总页`() {
        val json = """{"list":[],"total":0}"""

        val page = SerializationParser.parse(json, page = 1)

        assertEquals(0, page.totalItems)
        assertEquals(0, page.totalPages)
        assertEquals(emptyList(), page.items)
    }

    @Test
    fun `接口返回 error 时抛出 ParseResponseException`() {
        val json = """{"error":"參數錯誤"}"""

        val ex = assertFailsWith<ParseResponseException> {
            SerializationParser.parse(json, page = 1)
        }
        assertEquals(true, ex.message?.contains("參數錯誤") == true)
    }

    @Test
    fun `第 2 页每页 20 条时不回退为 30 导致总页数少算`() {
        val itemsJson = (1..20).joinToString(",") { i ->
            """{"id":"${3000 + i}","name":"作品$i"}"""
        }
        val json = """{"list":[$itemsJson],"total":45}"""

        val page = SerializationParser.parse(json, page = 2)

        assertEquals(2, page.currentPage)
        assertEquals(45, page.totalItems)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun `第 8 页为 30 条分页的最后不满页时仍保持 8 页`() {
        val itemsJson = (1..28).joinToString(",") { i ->
            """{"id":"${4000 + i}","name":"作品$i"}"""
        }
        val json = """{"list":[$itemsJson],"total":238}"""

        val page = SerializationParser.parse(json, page = 8)

        assertEquals(8, page.currentPage)
        assertEquals(238, page.totalItems)
        assertEquals(8, page.totalPages)
    }

    @Test
    fun `传入已知每页条数时第 2 页沿用第 1 页分页口径`() {
        val itemsJson = (1..20).joinToString(",") { i ->
            """{"id":"${5000 + i}","name":"作品$i"}"""
        }
        val json = """{"list":[$itemsJson],"total":50}"""

        val page = SerializationParser.parse(json, page = 2, knownPageSize = 20)

        assertEquals(2, page.currentPage)
        assertEquals(50, page.totalItems)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun `传入已知每页条数且本页不满页时总页数为当前页`() {
        val itemsJson = (1..10).joinToString(",") { i ->
            """{"id":"${6000 + i}","name":"作品$i"}"""
        }
        val json = """{"list":[$itemsJson],"total":50}"""

        val page = SerializationParser.parse(json, page = 3, knownPageSize = 20)

        assertEquals(3, page.currentPage)
        assertEquals(50, page.totalItems)
        assertEquals(3, page.totalPages)
    }
}
