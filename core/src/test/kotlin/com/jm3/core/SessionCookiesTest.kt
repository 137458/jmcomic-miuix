package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionCookiesTest {

    private fun cookie(name: String, value: String, domain: String, path: String = "/") =
        CookieSnapshot(name = name, value = value, domain = domain, path = path)

    @Test
    fun 会话Cookie_广播_每份Cookie覆盖全部线路域名() {
        val origin = listOf(cookie("AVS", "s1", "a.example"), cookie("sl", "g", "a.example"))

        val got = origin.broadcastTo(listOf("a.example", "b.example", "c.example"))

        assertEquals(
            listOf(
                "AVS" to "a.example",
                "sl" to "a.example",
                "AVS" to "b.example",
                "sl" to "b.example",
                "AVS" to "c.example",
                "sl" to "c.example",
            ),
            got.map { it.name to it.domain },
        )
        assertEquals("s1", got.first { it.name == "AVS" && it.domain == "c.example" }.value)
    }

    @Test
    fun 会话Cookie_广播_线路为空时保持原样() {
        val origin = listOf(cookie("AVS", "s1", "a.example"))

        val got = origin.broadcastTo(emptyList())

        assertEquals(origin, got)
    }

    @Test
    fun 会话Cookie_广播_跳过空域名且不重复展开() {
        val origin = listOf(cookie("AVS", "s1", "a.example"))

        val got = origin.broadcastTo(listOf("a.example", "  ", "a.example"))

        assertEquals(listOf("AVS" to "a.example"), got.map { it.name to it.domain })
    }

    @Test
    fun 会话Cookie_广播_保留原Cookie的路径() {
        val origin = listOf(cookie("AVS", "s1", "a.example", path = "/api"))

        val got = origin.broadcastTo(listOf("b.example"))

        assertEquals(listOf("a.example" to "/api", "b.example" to "/api"), got.map { it.domain to it.path })
    }

    @Test
    fun `测试 CookieManager 与 JavaNetCookieJar 的行为`() {
        val cookieManager = java.net.CookieManager().apply {
            setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL)
        }
        val cookieJar = okhttp3.JavaNetCookieJar(cookieManager)

        val cookie = okhttp3.Cookie.Builder()
            .name("AVS")
            .value("token123")
            .domain("18comic.vip")
            .path("/")
            .build()

        val saveUrl = okhttp3.HttpUrl.Builder().scheme("https").host("18comic.vip").build()
        cookieJar.saveFromResponse(saveUrl, listOf(cookie))

        val loaded = cookieJar.loadForRequest(saveUrl)
        println("Loaded cookies: $loaded")
        assertEquals(1, loaded.size)
    }

    @Test
    fun `测试 JmApiClient setCookies 与 getCookies`() {
        val builder = io.github.jukomu.jmcomic.core.config.JmConfiguration.Builder()
        val config = builder.build()
        val context = io.github.jukomu.jmcomic.core.net.OkHttpBuilder.build(config)
        val client = io.github.jukomu.jmcomic.core.client.impl.JmApiClient(
            config, context.client, context.cookieManager, context.domainManager
        )

        val cookie = okhttp3.Cookie.Builder()
            .name("AVS")
            .value("token123")
            .domain("18comic.vip")
            .path("/")
            .build()

        client.setCookies(listOf(cookie))

        println("client.cookies = ${client.cookies}")
        println("cookieStore.cookies = ${context.cookieManager.cookieStore.cookies}")

        val reqUrl = okhttp3.HttpUrl.Builder().scheme("https").host("18comic.vip").addPathSegment("favorite").build()
        val cookiesForReq = context.client.cookieJar.loadForRequest(reqUrl)
        println("cookiesForReq = $cookiesForReq")

        val user = com.jm3.core.model.Jm3User(
            uid = "1",
            username = "u",
            email = null,
            emailVerified = false,
            photoUrl = null,
            coin = 0,
            albumFavorites = 0,
            maxAlbumFavorites = 100,
            level = 1,
            levelName = null,
            nextLevelExp = 0L,
            currentExp = 0L,
            expPercent = 0
        )
        val session = com.jm3.core.model.Jm3Session(
            user = user,
            cookies = listOf(cookie("AVS", "token123", "18comic.vip"))
        )
    }

    @Test
    fun `sanitizeDomain 能够去除前导点、协议头、路径与端口`() {
        assertEquals("18comic.vip", sanitizeDomain(".18comic.vip"))
        assertEquals("18comic.vip", sanitizeDomain("..18comic.vip"))
        assertEquals("18comic.vip", sanitizeDomain("https://18comic.vip"))
        assertEquals("18comic.vip", sanitizeDomain("http://18comic.vip/"))
        assertEquals("18comic.vip", sanitizeDomain("https://18comic.vip:443/api"))
        assertEquals("18comic.vip", sanitizeDomain("  .18comic.vip/  "))
        assertEquals("", sanitizeDomain("   "))
    }

    @Test
    fun `broadcastTo 会清洗原始 cookie 和目标列表中的脏 domain 并去重`() {
        val origin = listOf(cookie("AVS", "s1", ".18comic.vip"))
        val got = origin.broadcastTo(listOf("https://18comic.vip", ".18comic.org", "  ", "18comic.org:443"))

        assertEquals(
            listOf("18comic.vip", "18comic.org"),
            got.map { it.domain }
        )
        // 确保所有产生的 cookie domain 都没有前导点，保证 OkHttp 绝不抛异常
        got.forEach {
            kotlin.test.assertFalse(it.domain.startsWith("."))
        }
    }

    @Test
    fun `toOkhttpCookie 对非法 domain 安全容错返回 null`() {
        val badCookie = cookie("AVS", "s1", "...")
        kotlin.test.assertNull(badCookie.toOkhttpCookie())

        val goodCookie = cookie("AVS", "s1", "18comic.vip")
        val okhttp = goodCookie.toOkhttpCookie()
        kotlin.test.assertNotNull(okhttp)
        assertEquals("18comic.vip", okhttp.domain)
    }

    @Test
    fun `restoreSession 遇到带前导点或脏域名的会话正常注入并不抛出异常且同步客户端状态`() {
        val config = io.github.jukomu.jmcomic.core.config.JmConfiguration.Builder().build()
        val context = io.github.jukomu.jmcomic.core.net.OkHttpBuilder.build(config)
        val client = io.github.jukomu.jmcomic.core.client.impl.JmApiClient(
            config, context.client, context.cookieManager, context.domainManager
        )
        val domainControls = object : DomainControls {
            override fun usedDomain(): String? = "18comic.vip"
            override fun domainsReady(): Boolean = true
            override fun candidateDomains(): Set<String> = setOf(".18comic.vip", "18comic.org")
            override fun coverHost(): String? = "18comic.vip"
            override fun domainLatency(): Map<String, Int> = emptyMap()
            override fun reprobeDomains() {}
            override fun close() {}
        }
        val gateway = Jm3GatewayImpl({ client }, { domainControls })

        val user = com.jm3.core.model.Jm3User(
            uid = "10086",
            username = "test_user",
            email = null,
            emailVerified = false,
            photoUrl = null,
            coin = 10,
            albumFavorites = 5,
            maxAlbumFavorites = 100,
            level = 2,
            levelName = "萌新",
            nextLevelExp = 1000L,
            currentExp = 200L,
            expPercent = 20
        )
        // 传入包含前导点和协议头的脏 cookie
        val session = com.jm3.core.model.Jm3Session(
            user = user,
            cookies = listOf(
                cookie("AVS", "my_secret_token", ".18comic.vip"),
                cookie("sl", "1", "https://18comic.org/api")
            )
        )

        // 验证不会抛出 IllegalArgumentException
        gateway.restoreSession(session)

        // 验证 client 内部字段通过反射同步
        val loggedInUserField = io.github.jukomu.jmcomic.core.client.AbstractJmClient::class.java.getDeclaredField("loggedInUserName").apply { isAccessible = true }
        assertEquals("test_user", loggedInUserField.get(client))

        val autoLoginField = io.github.jukomu.jmcomic.core.client.AbstractJmClient::class.java.getDeclaredField("autoLoginRefreshAllowed").apply { isAccessible = true }
        assertEquals(true, autoLoginField.get(client))

        val loginHostField = io.github.jukomu.jmcomic.core.client.AbstractJmClient::class.java.getDeclaredField("loginHost").apply { isAccessible = true }
        val host = loginHostField.get(client) as String
        kotlin.test.assertNotEquals("jm-placeholder.domain.com", host)
        kotlin.test.assertFalse(host.startsWith("."))
    }
}
