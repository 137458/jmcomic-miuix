package com.jm3.core

import io.github.jukomu.jmcomic.api.exception.ResponseException
import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration
import io.github.jukomu.jmcomic.core.net.OkHttpBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 恢复会话靠反射写库客户端的私有登录态；库升级改名或改签名时，
 * 这些调用会被 runCatching 吞掉，表现为「登录态看着在、鉴权请求却失效」，所以钉住依赖面。
 *
 * 这里不只断言成员存在，还真的调一遍库自己的加解密与重登判据：
 * 只写对字段名、却把密码写进解不出来的位置，是这个功能最容易悄悄坏掉的方式。
 */
class LibrarySessionReflectionTest {

    /** 库在响应提示里认这句话才自行重登（JmApiClient.shouldRefreshLogin 的判据常量）。 */
    private val sessionExpiredHint = "請先登入會員"

    private val usernameMethod = declared("cacheUsername", String::class.java)
    private val encryptMethod = declared("encryptPasswordInMemory", String::class.java)
    private val decryptMethod = AbstractJmClient::class.java
        .getDeclaredMethod("decryptPasswordFromMemory").apply { isAccessible = true }
    private val shouldRefreshMethod = JmApiClient::class.java
        .getDeclaredMethod("shouldRefreshLogin", ResponseException::class.java).apply { isAccessible = true }
    private val allowedField = field("autoLoginRefreshAllowed")
    private val passwordField = field("encryptedPassword")

    private fun declared(name: String, parameter: Class<*>): java.lang.reflect.Method =
        AbstractJmClient::class.java.getDeclaredMethod(name, parameter).apply { isAccessible = true }

    private fun field(name: String): java.lang.reflect.Field =
        AbstractJmClient::class.java.getDeclaredField(name).apply { isAccessible = true }

    /** 离线装配一个真实客户端：只走构造器与 OkHttp 装配，不发请求。 */
    private fun newClient(): JmApiClient {
        val config = JmConfiguration.Builder().build()
        val context = OkHttpBuilder.build(config)
        return JmApiClient(config, context.client, context.cookieManager, context.domainManager)
    }

    /** 复刻 Jm3GatewayImpl.primeCredentials 写进去的那组状态。 */
    private fun prime(client: JmApiClient, username: String, password: String) {
        usernameMethod.invoke(client, username)
        allowedField.setBoolean(client, true)
        passwordField.set(client, encryptMethod.invoke(client, password) as ByteArray)
    }

    private fun shouldRefresh(client: JmApiClient, message: String): Boolean =
        shouldRefreshMethod.invoke(client, ResponseException(message)) as Boolean

    @Test
    fun `库客户端的登录态成员仍是恢复会话依赖的那几个`() {
        listOf("cookieManager", "autoLoginRefreshAllowed", "loginHost", "encryptedPassword").forEach {
            assertEquals(it, field(it).name)
        }
        assertEquals(ByteArray::class.java, field("encryptedPassword").type)
        assertEquals(java.lang.Boolean.TYPE, field("autoLoginRefreshAllowed").type)
    }

    @Test
    fun `库客户端仍提供写入用户名与内存加密密码的方法`() {
        assertEquals(Void.TYPE, usernameMethod.returnType)
        assertEquals(ByteArray::class.java, encryptMethod.returnType)
        assertEquals(String::class.java, decryptMethod.returnType)
    }

    @Test
    fun `写进内存的用户名与密码能被库自己解出来`() {
        val client = newClient()

        prime(client, "alice", "s3cret")

        assertEquals("alice", field("cachedUserName").get(client))
        assertEquals("s3cret", decryptMethod.invoke(client) as String)
    }

    @Test
    fun `会话失效提示下库才自己重登`() {
        val client = newClient()
        prime(client, "alice", "s3cret")

        assertTrue(shouldRefresh(client, sessionExpiredHint))
        assertTrue(shouldRefresh(client, "请求失败：" + sessionExpiredHint))
        // 提示对不上就不该拿密码去重登，否则每个普通错误都会多打一次登录
        assertFalse(shouldRefresh(client, "网络连接超时"))
    }

    @Test
    fun `没有用户名或密码时库不重登`() {
        val noPassword = newClient()
        usernameMethod.invoke(noPassword, "alice")
        allowedField.setBoolean(noPassword, true)
        assertFalse(shouldRefresh(noPassword, sessionExpiredHint))

        val noUsername = newClient()
        allowedField.setBoolean(noUsername, true)
        passwordField.set(noUsername, encryptMethod.invoke(noUsername, "s3cret") as ByteArray)
        assertFalse(shouldRefresh(noUsername, sessionExpiredHint))

        // 忘记凭据等同于关掉自动重登：用户已主动登出，不能再拿旧密码登回去
        val forgotten = newClient()
        prime(forgotten, "alice", "s3cret")
        allowedField.setBoolean(forgotten, false)
        passwordField.set(forgotten, null)
        assertFalse(shouldRefresh(forgotten, sessionExpiredHint))
        assertTrue((decryptMethod.invoke(forgotten) as? String).isNullOrBlank())
    }
}
