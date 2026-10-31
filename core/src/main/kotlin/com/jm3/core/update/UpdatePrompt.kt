package com.jm3.core.update

/**
 * 更新提示策略：决定是否向用户弹出"发现新版本"，以及"忽略此版本"开关的取值。
 *
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
        val latest = latestVersion.trim()
        if (latest.isEmpty()) return false
        if (isIgnored(ignoredVersion, latest)) return false
        return promptedVersion?.trim() != latest
    }

    /** 忽略记录是否正好命中该版本（只忽略用户点过的那一版，后续更高版本仍会提示）。 */
    fun isIgnored(ignoredVersion: String?, latestVersion: String): Boolean {
        val latest = latestVersion.trim()
        if (latest.isEmpty()) return false
        return ignoredVersion?.trim() == latest
    }

    /** "忽略此版本"开关的下一个取值：已忽略则清除（null），否则记录当前版本。 */
    fun toggleIgnored(currentIgnored: String?, latestVersion: String): String? {
        val latest = latestVersion.trim()
        if (latest.isEmpty()) return null
        return if (isIgnored(currentIgnored, latest)) null else latest
    }
}
