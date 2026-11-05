package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import io.github.jukomu.jmcomic.core.client.AbstractJmClient

/**
 * 网关需要的那部分「库内非公开状态」：会话 Cookie 的读回与注入、库内登录身份的写入与清除。
 *
 * 库把 CookieStore 与登录身份收在 AbstractJmClient 的私有成员里，恢复会话只能反射读写；
 * 反射成员名、取不到时的降级、okhttp Cookie 的构造都是实现的私事，[Jm3GatewayImpl] 只认这个接口。
 * 客户端以 Any 传入：拍平后的 seam 上不出现库内部类型，测试可以塞任意替身。
 */
internal interface LibraryBridge {

    /** 库内 CookieStore 里此刻的全部 Cookie；取不到时为空表（此时只剩客户端直读那条路）。 */
    fun cookieStoreCookies(client: Any): List<CookieSnapshot>

    /** 把会话写进客户端：Cookie 铺到全部候选域名，并同步库内登录身份与登录域。 */
    fun restoreSession(client: Any, session: Jm3Session, targetDomains: Set<String>)

    /** 写入库内登录身份；password 为 null 表示只恢复身份，不打开自动重登。 */
    fun primeCredentials(client: Any, username: String, password: String?)

    /** 关闭库内自动重登并清掉内存密码。 */
    fun forgetCredentials(client: Any)
}

/**
 * [LibraryBridge] 的真实实现：按 jmcomic v1.1.12 的字节码反射取成员。
 *
 * 取不到成员不抛异常：对应能力静默降级，只留一条事实日志（只报成员名与异常类型，不打印成员值）。
 */
internal object ReflectiveLibraryBridge : LibraryBridge {

    private fun quiet(what: String, block: () -> Unit) {
        runCatching(block).onFailure { failure ->
            Jm3Log.warn(TAG, what + " 失败（" + failure.javaClass.name + "），相关能力降级")
        }
    }

    private fun <T> member(member: String, block: () -> T): T? =
        runCatching(block).getOrElse { failure ->
            Jm3Log.warn(TAG, "库内反射成员 " + member + " 取不到（" + failure.javaClass.name + "），相关能力降级")
            null
        }

    private val cookieManagerField: java.lang.reflect.Field? by lazy {
        member("cookieManager") {
            AbstractJmClient::class.java.getDeclaredField("cookieManager").apply { isAccessible = true }
        }
    }

    private val cacheUsernameMethod: java.lang.reflect.Method? by lazy {
        member("cacheUsername") {
            AbstractJmClient::class.java.getDeclaredMethod("cacheUsername", String::class.java)
                .apply { isAccessible = true }
        }
    }

    private val autoLoginRefreshAllowedField: java.lang.reflect.Field? by lazy {
        member("autoLoginRefreshAllowed") {
            AbstractJmClient::class.java.getDeclaredField("autoLoginRefreshAllowed").apply { isAccessible = true }
        }
    }

    private val loginHostField: java.lang.reflect.Field? by lazy {
        member("loginHost") {
            AbstractJmClient::class.java.getDeclaredField("loginHost").apply { isAccessible = true }
        }
    }

    /** 库的 protected 方法：用本客户端的内存密钥把明文密码加密后写进 encryptedPassword。 */
    private val encryptPasswordInMemoryMethod: java.lang.reflect.Method? by lazy {
        member("encryptPasswordInMemory") {
            AbstractJmClient::class.java.getDeclaredMethod("encryptPasswordInMemory", String::class.java)
                .apply { isAccessible = true }
        }
    }

    private val encryptedPasswordField: java.lang.reflect.Field? by lazy {
        member("encryptedPassword") {
            AbstractJmClient::class.java.getDeclaredField("encryptedPassword").apply { isAccessible = true }
        }
    }

    override fun cookieStoreCookies(client: Any): List<CookieSnapshot> =
        runCatching {
            val manager = cookieManagerField?.get(client) as? java.net.CookieManager
            manager?.cookieStore?.cookies.orEmpty().map {
                CookieSnapshot(
                    name = it.name,
                    value = it.value,
                    domain = it.domain.orEmpty(),
                    path = it.path.orEmpty(),
                )
            }
        }.getOrElse { failure ->
            Jm3Log.warn(TAG, "读库内 CookieStore 失败（" + failure.javaClass.name + "），会话回写只剩客户端直读")
            emptyList()
        }

    override fun restoreSession(client: Any, session: Jm3Session, targetDomains: Set<String>) {
        val library = client as? AbstractJmClient ?: return
        val okhttpCookies = session.cookies.broadcastTo(targetDomains).mapNotNull { it.toOkhttpCookie() }
        if (okhttpCookies.isNotEmpty()) {
            quiet("注入会话 Cookie") { library.setCookies(okhttpCookies) }
        }
        applyLoginState(library, session.user.username)
        quiet("写入库内登录域") {
            val bestHost = targetDomains.map { sanitizeDomain(it) }.firstOrNull { it.isNotBlank() }
                ?: "18comic.vip"
            loginHostField?.set(library, bestHost)
        }
    }

    override fun primeCredentials(client: Any, username: String, password: String?) {
        val library = client as? AbstractJmClient ?: return
        applyLoginState(library, username, password)
    }

    override fun forgetCredentials(client: Any) {
        val library = client as? AbstractJmClient ?: return
        quiet("关闭库内自动重登") { autoLoginRefreshAllowedField?.set(library, false) }
        quiet("清掉库内内存密码") { encryptedPasswordField?.set(library, null) }
    }

    /** 用户名、允许自动重登，以及拿得到时加密后的密码；密码缺省表示只恢复身份。 */
    private fun applyLoginState(client: AbstractJmClient, username: String, password: String? = null) {
        quiet("写入库内用户名") { cacheUsernameMethod?.invoke(client, username) }
        quiet("打开库内自动重登") { autoLoginRefreshAllowedField?.set(client, true) }
        if (password == null) return
        quiet("写入库内内存密码") {
            val encrypted = encryptPasswordInMemoryMethod?.invoke(client, password) as? ByteArray ?: return@quiet
            encryptedPasswordField?.set(client, encrypted)
        }
    }

    private const val TAG = "LibraryBridge"
}