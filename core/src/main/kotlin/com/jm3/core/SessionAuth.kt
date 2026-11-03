package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session

/**
 * 鉴权面：登录、登出、会话注入与凭据托管。
 *
 * 从 [Jm3Gateway] 抽出的父接口，唯一消费方是账号编排 [com.jm3.core.session.AccountManager]。
 * 目的不是拆小巨型接口（调用方不再用 `as?` 探测能力），而是让账号编排的协作者只依赖这 6 个方法：
 * 测试里手写假实现即可，不必伪造整张网关（旧做法是反射代理按方法名分派，接口改名时编译期毫无反应，
 * 只在运行时抛异常）。
 */
interface SessionAuth {
    /** 登录并返回会话（含 AVS Cookie 快照）。阻塞调用。 */
    fun login(username: String, password: String): Jm3Session

    /** 登出并清空库内会话。阻塞调用。 */
    fun logout()

    /** 恢复已持久化的会话：把 Cookie 注回库内 CookieJar。阻塞调用。 */
    fun restoreSession(session: Jm3Session)

    /**
     * 把本地凭据交给网关，让库的「会话失效 → 重登 → 重放」链路生效。阻塞调用。
     *
     * 库把登录身份与密码收在客户端实例内存里（JmApiClient.shouldRefreshLogin），那段内存每次冷启动必为空。
     * 实现方只允许把它留在内存，不得持久化明文。
     */
    fun primeCredentials(credentials: SavedCredentials)

    /** 抹掉网关内存里的登录凭据（登出时调用）：密码不再可解密，库也就不会自行重登。 */
    fun forgetCredentials()

    /** 网关此刻持有的会话 Cookie；取不到时返回空表，不抛异常。 */
    fun currentCookies(): List<CookieSnapshot>
}
