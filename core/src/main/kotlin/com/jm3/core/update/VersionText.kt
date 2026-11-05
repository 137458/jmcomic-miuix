package com.jm3.core.update

/**
 * 版本文本的唯一归一入口：去空白、去 v/V 前缀、去构建元数据（+ 之后），并解析出
 * 核心段与预发布段。比较策略在 [VersionCompare]，展示与"忽略此版本"记录都走这里，
 * 因此同一个版本不会在不同界面被当成两个版本。
 *
 * 非版本内容按宽松策略处理：非数字段记 0，恰好全为 0 的核心段折叠为空表，
 * 于是 "abc" 与 "0.0.0" 等价——本仓库只回答"要不要提示更新"，不承担版本号合法性校验。
 */
object VersionText {

    /** 归一后的结构化版本：核心段已去掉末尾的 0（1.2 与 1.2.0 段列相同）。 */
    data class Parsed(
        val core: List<Long>,
        val preRelease: List<String>?,
    )

    /** 展示用与记录用的规范文本：去掉 v/V 前缀与构建元数据；空白串归一为空串。 */
    fun normalized(raw: String?): String = raw.orEmpty()
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('+')
        .trim()

    /** 拆成核心段与预发布段，供 [VersionCompare] 比较。 */
    fun parse(raw: String?): Parsed {
        val text = normalized(raw)
        val coreText = text.substringBefore('-')
        val preText = text.substringAfter('-', missingDelimiterValue = "")
        val core = coreText.split('.')
            .filter { it.isNotEmpty() }
            .map { segment -> segment.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }
            .dropLastWhile { it == 0L }
        val preRelease = preText.split('.')
            .filter { it.isNotEmpty() }
            .takeIf { preText.isNotEmpty() }
        return Parsed(core, preRelease)
    }

    /** 两个版本串是否指同一版本：与 [VersionCompare.compare] 的 0 值严格一致。 */
    fun sameVersion(left: String?, right: String?): Boolean =
        VersionCompare.compare(left.orEmpty(), right.orEmpty()) == 0
}
