package com.jm3.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Release JSON 解析失败：区分“不是 JSON 对象”与“是对象但缺字段”，界面提示不同。 */
sealed class UpdateParseException(message: String) : Exception(message) {

    /** 响应体不是合法 JSON，或顶层不是 JSON 对象 */
    class Malformed(detail: String) : UpdateParseException("响应不是合法 JSON：$detail")

    /** 顶层是对象，但缺少必需字段 */
    class MissingField(val field: String) : UpdateParseException("响应缺少字段：$field")
}

/**
 * 解析 GitHub Releases API 的 latest 响应；失败抛 [UpdateParseException]。
 *
 * 用 :core 已有的 kotlinx-serialization-json，不新增依赖，也不建 @Serializable 模型：
 * 只取界面需要的几个字段，缺字段要报出具体字段名，直接把 JsonObject 逐字段读出来，
 * 比维护一套数据类 + 反序列化器更短且报错更准。JSON 是外部输入，字段可能缺失、
 * 为 null 或类型不符，这里统一按“取不到字符串就当空串”处理，
 * 必需字段（tag_name / html_url）为空再抛 [UpdateParseException.MissingField]。
 * assets 是可选数组，形状不符或元素不合格时降级处理（见 [parseAssets]），不影响主字段。
 */
object ReleaseJson {

    fun parse(text: String): ReleaseInfo {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (error: Exception) {
            throw UpdateParseException.Malformed(error.message ?: "无法解析")
        }
        val obj = element as? JsonObject
            ?: throw UpdateParseException.Malformed("顶层不是 JSON 对象")

        fun field(name: String): String = (obj[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

        val tagName = field("tag_name")
        if (tagName.isEmpty()) throw UpdateParseException.MissingField("tag_name")
        val htmlUrl = field("html_url")
        if (htmlUrl.isEmpty()) throw UpdateParseException.MissingField("html_url")

        return ReleaseInfo(
            tagName = tagName,
            name = field("name"),
            notes = field("body"),
            htmlUrl = htmlUrl,
            publishedAt = field("published_at"),
            assets = parseAssets(obj["assets"]),
        )
    }

    /**
     * 解析 assets 数组。
     *
     * 取舍：坏元素逐个跳过，而不是整条响应失败。安装包只是“省一次浏览器跳转”的增强，
     * 一个异常元素不该让整条更新信息作废；而必需字段（tag_name / html_url）缺失时仍然整条失败，
     * 因为那种响应连版本号都不完整。
     * size 取不到 Long（缺失、字符串、小数、null）一律按 0，由界面决定怎么展示未知大小。
     */
    private fun parseAssets(element: JsonElement?): List<ReleaseAsset> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item -> parseAsset(item) }
    }

    private fun parseAsset(element: JsonElement): ReleaseAsset? {
        val item = element as? JsonObject ?: return null
        val name = (item["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val downloadUrl = (item["browser_download_url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        // 没有文件名或地址就没有可下载目标，直接丢弃该项
        if (name.isEmpty() || downloadUrl.isBlank()) return null
        val size = (item["size"] as? JsonPrimitive)?.longOrNull ?: 0L
        return ReleaseAsset(name = name, size = size, downloadUrl = downloadUrl)
    }
}
