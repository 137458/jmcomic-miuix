package com.jm3.core

import com.jm3.core.model.CookieSnapshot

/**
 * 读取网关当前持有的会话 Cookie。
 *
 * 库在请求内自动重登（JmApiClient 的 shouldRefreshLogin 分支）之后会往自己的 cookieJar 写一份新 Cookie，
 * 而本地存的是登录那一刻的快照：不回写的话下次冷启动仍注入旧凭据，白跑一次登录。
 * [Jm3Gateway] 的签名不为此改动，能力单独成接口，调用方按需探测。
 */
interface SessionCookieSource {

    /** 客户端此刻持有的会话 Cookie；取不到时返回空表，不抛异常。 */
    fun currentCookies(): List<CookieSnapshot>
}
