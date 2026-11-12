package com.jm3.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import io.github.jukomu.jmcomic.api.exception.ParseResponseException

/**
 * `/serialization` 响应解析。
 *
 * 库内 `JmApiClient.getSerialization` 调了 `parseSearchPage`，只认 `content` 字段并按每页 80 条算总页数，
 * 与服务端实际返回的 `list` + `total` 结构不一致；这里按真实结构解析。
 */
internal object SerializationParser {

    /** 每页兜底条数，与网页端/接口常见分页一致；第 1 页实际不足 total 时优先用当页条数推算。 */
    private const val FALLBACK_PAGE_SIZE = 30

    fun parse(json: String, page: Int, covers: CoverResolver = CoverResolver.Unready): SearchPage {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val error = root.stringOrNull("error")?.trim()?.takeIf { it.isNotEmpty() }
            if (error != null) {
                throw ParseResponseException("API returned error: $error")
            }

            val totalItems = root.intOrZero("total").coerceAtLeast(0)
            val listArray = root.arrayOrNull("list") ?: JsonArray()
            val items = listArray.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                parseItem(element.asJsonObject, covers)
            }
            val pageSize = if (page == 1 && items.isNotEmpty() && items.size < totalItems) {
                items.size
            } else {
                FALLBACK_PAGE_SIZE
            }
            val totalPages = when {
                totalItems <= 0 && items.isEmpty() -> 0
                items.isEmpty() -> page
                totalItems <= 0 -> page
                page == 1 && items.size >= totalItems -> 1
                else -> maxOf(page, (totalItems + pageSize - 1) / pageSize)
            }
            return SearchPage(
                currentPage = page,
                totalPages = totalPages,
                totalItems = totalItems,
                items = items,
            )
        } catch (e: ParseResponseException) {
            throw e
        } catch (e: Exception) {
            throw ParseResponseException("Failed to parse serialization API JSON", e)
        }
    }

    private fun parseItem(obj: JsonObject, covers: CoverResolver): SearchResult? {
        val id = obj.stringOrNull("id")?.trim().orEmpty()
        if (id.isEmpty()) return null
        val title = obj.stringOrNull("name")?.trim()
            ?.ifEmpty { null }
            ?: obj.stringOrNull("title")?.trim().orEmpty()
        val authors = parseAuthors(obj.get("author"))
        val category = obj.categoryTitle("category") ?: obj.categoryTitle("category_sub")
        return SearchResult(
            id = id,
            title = title,
            authors = authors,
            tags = emptyList(),
            coverUrl = covers.resolve(id).urlOrNull,
            category = category,
        )
    }

    private fun parseAuthors(element: JsonElement?): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            return element.asString.trim().takeIf { it.isNotEmpty() }?.let(::listOf) ?: emptyList()
        }
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { item ->
                if (item.isJsonNull || !item.isJsonPrimitive) null
                else item.asString.trim().takeIf { it.isNotEmpty() }
            }
        }
        return emptyList()
    }

    private fun JsonObject.categoryTitle(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonObject) return null
        return el.asJsonObject.stringOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return el.asString
    }

    private fun JsonObject.intOrZero(key: String): Int {
        val el = get(key) ?: return 0
        if (el.isJsonNull || !el.isJsonPrimitive) return 0
        val prim = el.asJsonPrimitive
        return if (prim.isNumber) prim.asInt else prim.asString.trim().toIntOrNull() ?: 0
    }

    private fun JsonObject.arrayOrNull(key: String): JsonArray? {
        val el = get(key) ?: return null
        return if (el.isJsonArray) el.asJsonArray else null
    }
}
