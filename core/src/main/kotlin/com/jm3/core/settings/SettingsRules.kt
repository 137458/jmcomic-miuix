package com.jm3.core.settings

import com.jm3.core.CoverLocator

/**
 * 搜索历史规则：折行、去空白、去重前移、按上限截断。
 *
 * 文本按行落盘，所以单条内容里的换行必须折叠成空格 —— 否则写进去再读出来会变成两条，
 * 而「读-改-写」还会把一次输入拆成多次记录。
 */
object SearchHistory {

    const val LIMIT = 10

    private val LINE_BREAK = Regex("[\\r\\n]+")

    /** 折行成空格并去首尾空白；全空白返回 null（不写入）。 */
    fun normalize(raw: String): String? =
        raw.replace(LINE_BREAK, " ").trim().takeIf { it.isNotEmpty() }

    /** 新词置顶、同词去重、超出 [LIMIT] 截断；空词原样返回旧表。 */
    fun add(existing: List<String>, query: String): List<String> {
        val normalized = normalize(query) ?: return existing
        return (listOf(normalized) + existing.filterNot { it == normalized }).take(LIMIT)
    }

    fun remove(existing: List<String>, query: String): List<String> =
        existing.filterNot { it == query }

    /** 解码按行存储的文本：逐行归一，空行丢弃。 */
    fun decode(raw: String?): List<String> =
        raw?.split('\n')?.mapNotNull { normalize(it) } ?: emptyList()

    /** 编码：一条一行；先归一再拼，保证 `decode(encode(x))` 与 `x` 归一后一致。 */
    fun encode(items: List<String>): String =
        items.mapNotNull { normalize(it) }.joinToString("\n")
}

/** 小说阅读偏好规则：字号夹取到可读区间，语言只接受 tw/cn。 */
object NovelPreferences {

    const val MIN_FONT_SIZE = 14
    const val MAX_FONT_SIZE = 26
    const val DEFAULT_FONT_SIZE = 18
    const val DEFAULT_LANG = "tw"

    fun clampFontSize(sizeSp: Int): Int = sizeSp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

    fun normalizeLang(lang: String): String = if (lang == "cn") "cn" else DEFAULT_LANG
}

/**
 * 设置项里的版本号文本归一：去首尾空白，空白串按「未设置」处理。
 *
 * 只管「有没有存过版本号」，不做版本比较 —— 判断两个版本是否等价一律走
 * [com.jm3.core.update.VersionText.sameVersion]。两个同名对象并存会让人以为这里也能比版本，
 * 因此不叫 VersionText：这里存的是用户可见文本，那边才是版本语义。
 */
object VersionSettingText {

    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}

/** 每日签到日期文本归一：仅保留合法 ISO 日期（YYYY-MM-DD），非法文本视为未记录。 */
object CheckInDateRecord {

    private val ISO_DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { ISO_DATE_PATTERN.matches(it) } ?: return null
        return runCatching { java.time.LocalDate.parse(trimmed).toString() }.getOrNull()
    }
}
