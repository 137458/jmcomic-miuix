package com.jm3.core

import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 恢复会话靠反射写库客户端的私有登录态；库升级改名或改签名时，
 * 这些调用会被 runCatching 吞掉，表现为「登录态看着在、鉴权请求却失效」，所以钉住依赖面。
 */
class LibrarySessionReflectionTest {

    private fun field(name: String) = AbstractJmClient::class.java.getDeclaredField(name)

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
        val cacheUsername = AbstractJmClient::class.java.getDeclaredMethod("cacheUsername", String::class.java)
        assertEquals(Void.TYPE, cacheUsername.returnType)

        val encrypt = AbstractJmClient::class.java.getDeclaredMethod("encryptPasswordInMemory", String::class.java)
        assertEquals(ByteArray::class.java, encrypt.returnType)
    }
}
