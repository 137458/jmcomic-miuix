package com.jm3.core.update

/**
 * 远端版本相对本地版本的位置。
 *
 * 三个取值直接对应界面三态：NEWER 提示更新，SAME 已是最新，OLDER 表示本地已领先
 * （本地装了开发版/预发布版时会遇到，此时不应反向“降级提示”）。
 */
enum class VersionRelation {
    /** 远端比本地高 */
    NEWER,

    /** 两侧等价 */
    SAME,

    /** 远端比本地低 */
    OLDER,
}

/**
 * GitHub Release 里本应用真正用到的字段。
 *
 * 只保留界面需要的部分：版本、标题、更新说明、Release 页面、发布时间。assets 不建模，
 * 因为本应用不做应用内 APK 下载，下载按钮只打开 html_url（见 UpdateScreen 的取舍说明）。
 */
data class ReleaseInfo(
    /** 原始 tag，形如 v1.1.0；GitHub 不保证带 v 前缀 */
    val tagName: String,
    /** Release 标题，可能为空串 */
    val name: String,
    /** Release 说明（Markdown 原文，不做渲染） */
    val notes: String,
    /** Release 页面地址（html_url），下载按钮打开的目标 */
    val htmlUrl: String,
    /** 发布时间原文（ISO-8601，如 2026-10-31T06:12:34Z），可能为空串 */
    val publishedAt: String,
) {
    /** 去掉 v 前缀的展示版本号：v1.1.0 → 1.1.0 */
    val version: String
        get() = tagName.trim().removePrefix("v").removePrefix("V")

    /** 发布时间只取日期部分：2026-10-31T06:12:34Z → 2026-10-31；非 ISO 形态原样返回 */
    val publishedDate: String
        get() = if (publishedAt.length >= 10 && publishedAt[4] == '-' && publishedAt[7] == '-') {
            publishedAt.take(10)
        } else {
            publishedAt
        }
}

/** 检查更新失败的原因分类；界面按分类给不同提示，不必解析异常类型。 */
enum class UpdateFailure {
    /** 连接/读取超时、DNS、断网等传输层失败 */
    NETWORK,

    /** GitHub 返回 404：仓库没有任何 Release */
    NOT_FOUND,

    /** GitHub 返回 403/429：接口限流 */
    RATE_LIMITED,

    /** 其他非 2xx 状态码 */
    HTTP,

    /** 响应不是合法 JSON，或响应体为空 */
    RESPONSE,

    /** 响应是 JSON 但缺少必需字段（tag_name / html_url） */
    MISSING_FIELD,
}

/**
 * 检查更新的结果。
 *
 * 取舍：返回 sealed 结果而不是抛异常。检查更新是“用户点一下就有明确三分支”的动作，
 * 失败原因本身就是界面要展示的内容（断网/限流/无 Release），用返回值承载比 try/catch 更直接，
 * 也让调用方无法忘记处理失败分支。
 */
sealed interface UpdateCheckResult {

    /**
     * 请求成功。[relation] 由 [VersionCompare] 判定：NEWER 才提示更新；
     * SAME/OLDER 都按“已是最新”展示。
     */
    data class Success(
        val release: ReleaseInfo,
        val relation: VersionRelation,
    ) : UpdateCheckResult

    /** 请求失败。[message] 可直接显示给用户。 */
    data class Failed(
        val failure: UpdateFailure,
        val detail: String,
    ) : UpdateCheckResult {
        val message: String
            get() = when (failure) {
                UpdateFailure.NETWORK -> "网络请求失败：$detail"
                UpdateFailure.NOT_FOUND -> "仓库暂无 Release（$detail）"
                UpdateFailure.RATE_LIMITED -> "GitHub 接口限流，请稍后再试（$detail）"
                UpdateFailure.HTTP -> "请求失败：$detail"
                UpdateFailure.RESPONSE -> "响应格式异常：$detail"
                UpdateFailure.MISSING_FIELD -> "响应缺少字段：$detail"
            }
    }
}
