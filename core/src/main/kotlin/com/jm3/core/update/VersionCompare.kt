package com.jm3.core.update

/**
 * 版本号比较（当前仅用于更新检测）。
 *
 * 文本归一（去空白、去 v/V 前缀、去构建元数据）只由 [VersionText] 做，这里只负责比较策略：
 * 核心段逐段按数值比，缺段补 0；预发布段按 SemVer
 * 规则比：正式版大于同号预发布版，标识符逐段比，纯数字段按数值、其余按字典序，
 * 前缀相同则短者更小。
 *
 * 宽松策略：非数字内容一律按 0 处理（本仓库只做“要不要提示更新”，不承担
 * 严格校验版本号合法性的职责，解析失败也不应阻断检查）。
 */
object VersionCompare {

    /** 比较两个版本号：a 大于 b 返回正数，相等返回 0，小于返回负数。 */
    fun compare(a: String, b: String): Int {
        val left = VersionText.parse(a)
        val right = VersionText.parse(b)

        val coreSize = maxOf(left.core.size, right.core.size)
        for (index in 0 until coreSize) {
            val result = left.core.getOrElse(index) { 0L }.compareTo(right.core.getOrElse(index) { 0L })
            if (result != 0) return result
        }

        return comparePreRelease(left.preRelease, right.preRelease)
    }

    /** 远端版本相对本地版本的位置。 */
    fun relation(local: String, remote: String): VersionRelation = when (compare(remote, local)) {
        in 1..Int.MAX_VALUE -> VersionRelation.NEWER
        0 -> VersionRelation.SAME
        else -> VersionRelation.OLDER
    }

    private fun comparePreRelease(left: List<String>?, right: List<String>?): Int {
        if (left == null && right == null) return 0
        // 没有预发布段的正式版高于同号预发布版
        if (left == null) return 1
        if (right == null) return -1

        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            // 前缀相同则短者更小：1.0.0-beta < 1.0.0-beta.1
            if (index >= left.size) return -1
            if (index >= right.size) return 1

            val l = left[index]
            val r = right[index]
            val lNumber = l.toLongOrNull()
            val rNumber = r.toLongOrNull()

            val result = when {
                lNumber != null && rNumber != null -> lNumber.compareTo(rNumber)
                // 纯数字标识符低于字母标识符（SemVer 规则，本接口暂未出现，兜住即可）
                lNumber != null -> -1
                rNumber != null -> 1
                else -> l.compareTo(r)
            }
            if (result != 0) return result
        }
        return 0
    }

}
