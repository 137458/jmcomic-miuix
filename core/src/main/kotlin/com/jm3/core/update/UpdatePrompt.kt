package com.jm3.core.update

/**
 * 更新提示策略：决定是否向用户弹出"发现新版本"，以及"忽略此版本"开关的取值。
 *
 * 版本比较一律走 [VersionText]：「1.2」「v1.2.0」「1.2.0+build」都算同一版本，
 * 否则用户点了忽略、下次仍会因为写法不同再弹一次。
 * 空白字符串按"未设置"处理，避免把空 tag 当成有效忽略记录。
 */
object UpdatePrompt {

    /**
     * 是否应在启动期提示该版本：未设置忽略、且本次会话还没提示过同一版本。
     */
    fun shouldNotify(
        latestVersion: String,
        ignoredVersion: String?,
        promptedVersion: String?,
    ): Boolean {
        val latest = VersionText.normalized(latestVersion)
        if (latest.isEmpty()) return false
        if (isIgnored(ignoredVersion, latest)) return false
        return !VersionText.sameVersion(promptedVersion, latest)
    }

    /** 忽略记录是否正好命中该版本（只忽略用户点过的那一版，后续更高版本仍会提示）。 */
    fun isIgnored(ignoredVersion: String?, latestVersion: String): Boolean {
        val latest = VersionText.normalized(latestVersion)
        if (latest.isEmpty()) return false
        return VersionText.sameVersion(ignoredVersion, latest)
    }

    /** "忽略此版本"开关的下一个取值：已忽略则清除（null），否则记录当前版本。 */
    fun toggleIgnored(currentIgnored: String?, latestVersion: String): String? {
        // 记录写成规范文本（1.2 而不是 v1.2），下次比较才与远端 tag 的写法无关
        val latest = VersionText.normalized(latestVersion)
        if (latest.isEmpty()) return null
        return if (isIgnored(currentIgnored, latest)) null else latest
    }
}
