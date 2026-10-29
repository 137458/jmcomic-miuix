package com.jm3.core

/**
 * 章节标题的展示回落。
 *
 * JM 相册的 `series` 条目允许缺少 `name`（库内解析为标题空串），直接拼接会得到空白行、
 * 以及组标题前悬空的 " ~ "。这里统一按位置补一个话数标题：位置来自相册内序号（0 起），
 * 与分组切片的起始位置一致，确保跨组编号连续。
 */
object ChapterLabels {

    /** 章节行标题：有标题原样返回，空标题（含纯空白）回落为「第N话」。 */
    fun chapterTitle(title: String, position: Int): String =
        title.ifBlank { "第${position + 1}话" }

    /**
     * 章节组标题：取首末非空标题拼 "首 ~ 末"。
     *
     * - 只有一个已知标题时直接用它，不留下悬空分隔符；
     * - 全组都缺标题时回落为话数区间（单话组回落为单个话数）。
     */
    fun groupTitle(titles: List<String>, startPosition: Int): String {
        val named = titles.filter { it.isNotBlank() }
        if (named.isEmpty()) {
            val first = startPosition + 1
            return if (titles.size > 1) "第${first}–${startPosition + titles.size}话" else "第${first}话"
        }
        return if (named.size == 1) named.first() else "${named.first()} ~ ${named.last()}"
    }
}
