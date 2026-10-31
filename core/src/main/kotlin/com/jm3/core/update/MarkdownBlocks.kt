package com.jm3.core.update

/** Release 说明正文的展示块。 */
sealed interface MarkdownBlock {

    /** 标题，[level] 为井号个数（1..6） */
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** 无序列表项 */
    data class Bullet(val text: String) : MarkdownBlock

    /** 有序列表项，[number] 保留原文序号（1. 2. 3. 可能与渲染顺序不一致） */
    data class Ordered(val number: Int, val text: String) : MarkdownBlock

    /** 分隔线 */
    data object Divider : MarkdownBlock

    /** 段落，连续的非空普通行以单个空格连接 */
    data class Paragraph(val text: String) : MarkdownBlock
}

/**
 * 把 Release 的 Markdown 正文切成展示块，交给 UI 直接排版。
 *
 * 取舍：只做“分块”不做行内解析（不加粗、不解析链接、不渲染 HTML）。更新说明是外部输入，
 * 完整 Markdown 渲染要么引入依赖，要么自己写一套行内解析器；而 Release 说明的实际结构就是
 * 标题 + 列表 + 段落，切块后由 Compose 负责字号与缩进，既够用又不会把外部 HTML 带进界面。
 *
 * 识别规则按行判断，顺序为：空行（断段落）→ 标题 → 分隔线 → 无序列表 → 有序列表 → 普通行。
 * 分隔线必须整行由同一个符号（- * _）组成且长度不小于 3，避免把 "- 修复" 这类列表项误判成横线。
 */
object MarkdownBlocks {

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = StringBuilder()

        // 段落是连续普通行的累积，遇到块级行或空行时落地（空段落不产出块）。
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.toString())
                paragraph.setLength(0)
            }
        }

        for (rawLine in markdown.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                flushParagraph()
                continue
            }

            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                flushParagraph()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                continue
            }

            if (isDivider(line)) {
                flushParagraph()
                blocks += MarkdownBlock.Divider
                continue
            }

            val bullet = BULLET.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(bullet.groupValues[1].trim())
                continue
            }

            val ordered = ORDERED.matchEntire(line)
            if (ordered != null) {
                flushParagraph()
                blocks += MarkdownBlock.Ordered(
                    number = ordered.groupValues[1].toInt(),
                    text = ordered.groupValues[2].trim(),
                )
                continue
            }

            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(line)
        }
        flushParagraph()
        return blocks
    }

    /** 整行同符号重复 >= 3 才算分隔线；"- 修复" 这类列表项因此不会被误判。 */
    private fun isDivider(line: String): Boolean {
        if (line.length < 3) return false
        val mark = line[0]
        if (mark != '-' && mark != '*' && mark != '_') return false
        return line.all { it == mark }
    }
}

/** 标题：井号 1..6 个，必须跟空白，否则 "#标题" 这类文本按段落处理。 */
private val HEADING = Regex("""^(#{1,6})\s+(.+)$""")

/** 无序列表：- * + 后必须跟空白。 */
private val BULLET = Regex("""^[-*+]\s+(.+)$""")

/** 有序列表：数字加点加空白；数字限 9 位，保证 toInt 不会溢出。 */
private val ORDERED = Regex("""^(\d{1,9})\.\s+(.+)$""")
