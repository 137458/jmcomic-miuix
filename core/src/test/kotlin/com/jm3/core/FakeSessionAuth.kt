package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session

/**
 * 手写的鉴权假实现：只实现 [SessionAuth] 这 6 个方法，行为全部由构造参数注入。
 *
 * 旧做法用反射代理伪造整张网关接口，按方法名字符串分派：接口改名在编译期毫无反应，只在运行时抛异常。
 * 换成真类后，多实现一个、少实现一个都过不了编译。
 *
 * 放在 [SessionAuth] 同包下：替身贴着自己所替代的接口，:core 里任何只要鉴权面的用例都能直接复用。
 * 注意 :app 的测试看不到本文件（模块测试源码不互相导出），需要整张网关替身时另建。
 */
internal class FakeSessionAuth(
    /** 没配置时 login 直接失败：用例真走到登录就说明假网关缺桩，不能悄悄返回一个假会话。 */
    private val onLogin: ((String, String) -> Jm3Session)? = null,
    private val onPrimeCredentials: (SavedCredentials) -> Unit = {},
    private val onCurrentCookies: () -> List<CookieSnapshot> = { emptyList() },
    private val onForgetCredentials: () -> Unit = {},
    /** 必须留在最后一个参数：多个用例用尾随 lambda 配置会话注入行为。 */
    private val onRestoreSession: (Jm3Session) -> Unit = {},
) : SessionAuth {

    override fun login(username: String, password: String): Jm3Session =
        onLogin?.invoke(username, password)
            ?: error("该用例未配置 onLogin，FakeSessionAuth 不应被要求登录")

    /** 登出后的存储清理由 AccountManager 自己断言，这里只保证不抛异常。 */
    override fun logout() = Unit

    override fun restoreSession(session: Jm3Session) = onRestoreSession(session)

    override fun primeCredentials(credentials: SavedCredentials) = onPrimeCredentials(credentials)

    override fun forgetCredentials() = onForgetCredentials()

    override fun currentCookies(): List<CookieSnapshot> = onCurrentCookies()
}
