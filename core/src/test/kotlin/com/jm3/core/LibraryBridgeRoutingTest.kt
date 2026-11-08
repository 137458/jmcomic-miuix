package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 网关与库内私有状态之间的调用面：restoreSession/primeCredentials/forgetCredentials/currentCookies
 * 必须全部经过 [LibraryBridge]，网关自己不再反射库成员、不再广播 Cookie。
 */
class LibraryBridgeRoutingTest {

    private class FakeBridge : LibraryBridge {
        var cookieStoreResult: List<CookieSnapshot> = emptyList()
        var restored: Pair<Jm3Session, Set<String>>? = null
        var primed: Pair<String, String?>? = null
        var forgotCount = 0

        override fun cookieStoreCookies(client: Any): List<CookieSnapshot> = cookieStoreResult

        override fun restoreSession(client: Any, session: Jm3Session, targetDomains: Set<String>) {
            restored = session to targetDomains
        }

        override fun primeCredentials(client: Any, username: String, password: String?) {
            primed = username to password
        }

        override fun forgetCredentials(client: Any) {
            forgotCount++
        }
    }

    private class FakeDomainControls(private val domains: Set<String>) : DomainControls {
        override fun usedDomain(): String? = null
        override fun domainsReady(): Boolean = true
        override fun candidateDomains(): Set<String> = domains
        override fun coverHost(): String? = null
        override fun domainLatency(): Map<String, Int> = emptyMap()
        override fun reprobeDomains() = Unit
        override fun close() = Unit
    }

    private fun libraryClient(): io.github.jukomu.jmcomic.core.client.impl.JmApiClient {
        val config = io.github.jukomu.jmcomic.core.config.JmConfiguration.Builder().build()
        val context = io.github.jukomu.jmcomic.core.net.OkHttpBuilder.build(config)
        return io.github.jukomu.jmcomic.core.client.impl.JmApiClient(
            config, context.client, context.cookieManager, context.domainManager,
        )
    }

    private fun gateway(bridge: LibraryBridge, domains: Set<String> = setOf("a.example")): Jm3GatewayImpl {
        val client = libraryClient()
        return Jm3GatewayImpl(
            clientProvider = { client },
            domainControlsProvider = { FakeDomainControls(domains) },
            bridge = bridge,
        )
    }

    private val cookie = CookieSnapshot(name = "AVS", value = "s1", domain = ".18comic.vip", path = "/")

    private fun session() = Jm3Session(
        user = Jm3User(
            uid = "1", username = "u", email = null, emailVerified = false, photoUrl = null,
            coin = 0, albumFavorites = 0, maxAlbumFavorites = 100, level = 1, levelName = null,
            nextLevelExp = 0L, currentExp = 0L, expPercent = 0,
        ),
        cookies = listOf(cookie),
    )

    @Test
    fun restoreSession把会话与目标域名交给bridge() {
        val bridge = FakeBridge()
        gateway(bridge).restoreSession(session())

        val (forwarded, domains) = bridge.restored ?: error("restoreSession 未经过 bridge")
        assertEquals(listOf(cookie), forwarded.cookies, "Cookie 广播是 bridge 的事，网关只转交原始会话")
        assertTrue("a.example" in domains, "候选域名要参与目标域名，实际 $domains")
        assertTrue("18comic.vip" in domains, "官方镜像要兜底，实际 $domains")
        assertTrue("18comic.org" in domains, "官方镜像要兜底，实际 $domains")
    }

    @Test
    fun restoreSession在候选域名读取失败时仍然交给bridge() {
        val bridge = FakeBridge()
        val client = libraryClient()
        val gw = Jm3GatewayImpl(
            clientProvider = { client },
            domainControlsProvider = { throw IllegalStateException("域名能力未就绪") },
            bridge = bridge,
        )
        gw.restoreSession(session())

        val domains = bridge.restored?.second ?: error("restoreSession 未经过 bridge")
        assertTrue("18comic.vip" in domains && "18comic.org" in domains, "镜像兜底不应依赖域名能力，实际 $domains")
    }

    @Test
    fun primeCredentials把用户名密码交给bridge() {
        val bridge = FakeBridge()
        gateway(bridge).primeCredentials(SavedCredentials(username = "u", password = "p"))

        assertEquals("u" to "p", bridge.primed)
    }

    @Test
    fun forgetCredentials交给bridge() {
        val bridge = FakeBridge()
        gateway(bridge).forgetCredentials()

        assertEquals(1, bridge.forgotCount)
    }

    @Test
    fun currentCookies在客户端直读为空时用bridge的CookieStore并清洗域名() {
        val bridge = FakeBridge().apply {
            cookieStoreResult = listOf(CookieSnapshot(name = "AVS", value = "s1", domain = ".18comic.vip", path = ""))
        }
        val got = gateway(bridge).currentCookies()

        assertEquals(listOf("AVS" to "18comic.vip"), got.map { it.name to it.domain })
        assertEquals("/", got.single().path, "空路径要补成 /")
    }

    @Test
    fun currentCookies在两条来源都为空时给空表() {
        assertTrue(gateway(FakeBridge()).currentCookies().isEmpty())
    }
}