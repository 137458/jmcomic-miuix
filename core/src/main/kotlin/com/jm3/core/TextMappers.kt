package com.jm3.core



private val HTML_BREAK = Regex("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>")
private val HTML_TAG = Regex("<[^>]*>")
private val HTML_BLANK_LINES = Regex("\n{3,}")
private val HTML_ENTITY = Regex("&(?:nbsp|amp|lt|gt|quot|#39);")

/** 作品描述在接口里是 HTML；转成纯文本供 Compose 排版，保留段落换行。 */
fun htmlToText(html: String?): String {
    val raw = html?.takeIf { it.isNotBlank() } ?: return ""
    return raw
        .replace(HTML_BREAK, "\n")
        .replace(HTML_TAG, "")
        .replace(HTML_ENTITY) { match ->
            when (match.value) {
                "&nbsp;" -> " "
                "&amp;" -> "&"
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&#39;" -> "'"
                else -> match.value
            }
        }
        .replace(HTML_BLANK_LINES, "\n\n")
        .trim()
}
