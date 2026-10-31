package com.jm3.core

/** 会话失效时给用户看的统一文案：说清要做什么，而不是把服务端 JSON 复述给用户。 */
const val LOGIN_EXPIRED_MESSAGE: String = "登录已失效，请重新登录"

// 简体与繁体两套服务端文案都要认：同一套接口在不同入口返回的提示语并不统一。
private val loginExpiredKeywords = listOf(
    "請先登入會員",
    "請先登入",
    "尚未登入",
    "请先登入",
    "请先登录",
    "尚未登录",
)

// 宽松匹配 JSON 里的业务码：允许键值之间空白、值带引号，并要求 401 之后不再跟数字（避免把 4010 当成 401）。
private val code401 = Regex(""""code"\s*:\s*"?401"?(?!\d)""")

/**
 * 判断一条异常文案是否属于「登录已失效」。
 *
 * 为什么按文本判断：服务端在会话过期时不返回空数据，而是回一段业务 JSON（例如
 * `{"code":401,"data":"...","errorMsg":"請先登入會員"}`），网关把它当失败抛出，
 * 但 Throwable.message 里装的是响应体原文——:app 侧只拿得到一个字符串，没有结构化字段可读。
 *
 * 命中条件（任一即可）：
 * - 出现服务端登录失效提示（`請先登入會員`、`尚未登入`、`请先登录` 等）；
 * - 文案是 JSON 且业务码为 401，或文案本身就是一个 401。
 * 其余情况（500、超时、普通网络错误等）都返回 false，交给调用方按普通错误处理。
 */
fun isSessionExpired(message: String?): Boolean {
    val text = message?.trim().orEmpty()
    if (text.isEmpty()) return false
    // 少数入口只把状态码丢过来，没有响应体
    if (text == "401") return true
    if (loginExpiredKeywords.any { text.contains(it) }) return true
    return code401.containsMatchIn(text)
}
