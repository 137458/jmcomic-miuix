package com.jm3.core.session

import com.jm3.core.CredentialStorage
import com.jm3.core.SavedCredentials
import com.jm3.core.SessionAuth
import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AccountManagerTest {

    private fun testUser() = Jm3User(
        uid = "123",
        username = "test_user",
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
        expPercent = 0,
    )

    private fun fakeGateway(
        onLogin: ((String, String) -> Jm3Session)? = null,
        onPrimeCredentials: (SavedCredentials) -> Unit = {},
        onCurrentCookies: () -> List<CookieSnapshot> = { emptyList() },
        onForgetCredentials: () -> Unit = {},
        onRestoreSession: (Jm3Session) -> Unit = {},
    ): FakeGateway = FakeGateway(
        onLogin = onLogin,
        onPrimeCredentials = onPrimeCredentials,
        onCurrentCookies = onCurrentCookies,
        onForgetCredentials = onForgetCredentials,
        onRestoreSession = onRestoreSession,
    )

    /**
     * 手写的鉴权假实现：只实现 [SessionAuth] 这 6 个方法，行为全部由构造参数注入。
     *
     * 旧做法用反射代理伪造整张网关接口，按方法名字符串分派：接口改名在编译期毫无反应，只在运行时抛异常。
     * 换成真类后，多实现一个、少实现一个都过不了编译。
     */
    private class FakeGateway(
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
                ?: error("该用例未配置 onLogin，FakeGateway 不应被要求登录")

        /** 登出后的存储清理由 AccountManager 自己断言，这里只保证不抛异常。 */
        override fun logout() = Unit

        override fun restoreSession(session: Jm3Session) = onRestoreSession(session)

        override fun primeCredentials(credentials: SavedCredentials) = onPrimeCredentials(credentials)

        override fun forgetCredentials() = onForgetCredentials()

        override fun currentCookies(): List<CookieSnapshot> = onCurrentCookies()
    }

    private class FakeCredentialStorage(var stored: SavedCredentials? = null) : CredentialStorage {
        var clearCount = 0
        var saveCount = 0
        override suspend fun load(): SavedCredentials? = stored
        override suspend fun save(credentials: SavedCredentials) {
            stored = credentials
            saveCount++
        }
        override suspend fun clear() {
            stored = null
            clearCount++
        }
    }

    /** 每次构造都要新网关时用它：keeps 同一实例，便于 AccountManager 做身份比较。 */
    private fun account(
        store: SessionStorage,
        credentials: CredentialStorage = FakeCredentialStorage(),
        gateway: () -> SessionAuth = { fakeGateway() },
    ): AccountManager = AccountManager(gateway, store, credentials)

    private class FakeSessionStore(var stored: Jm3Session? = null) : SessionStorage {
        var clearCount = 0
        var saveCount = 0

        /** 静默重登连续失败次数：按落盘语义跨 AccountManager 实例共享。 */
        var renewFailures = 0
        override suspend fun load(): Jm3Session? = stored
        override suspend fun save(session: Jm3Session) {
            stored = session
            saveCount++
        }
        override suspend fun clear() {
            stored = null
            clearCount++
        }
        override suspend fun renewFailureCount(): Int = renewFailures
        override suspend fun setRenewFailureCount(count: Int) {
            renewFailures = count
        }
    }

    @Test
    fun `restore 在本地有会话时立即恢复用户状态，且网关注入抛出瞬时异常时绝不删除本地会话`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val store = FakeSessionStore(stored = session)
        var injected = false
        val gateway = fakeGateway {
            injected = true
            throw RuntimeException("网络瞬时超时或探活未完成")
        }
        val account = AccountManager({ gateway }, store, FakeCredentialStorage())

        account.restore()

        // 验证用户状态成功恢复，不掉登录态
        assertEquals(session.user, account.user.value)
        // 验证网关 restoreSession 确实被调用过
        assertEquals(true, injected)
        // 关键断言：绝不能把存储清空！
        assertEquals(0, store.clearCount)
        assertEquals(session, store.stored)
    }

    @Test
    fun `restore 本地无会话时保持未登录`() = runBlocking {
        val store = FakeSessionStore(stored = null)
        val account = AccountManager({ fakeGateway() }, store, FakeCredentialStorage())

        account.restore()

        assertNull(account.user.value)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `markSessionExpired 清除存储并将用户置空`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        val gateway = fakeGateway()
        val account = AccountManager({ gateway }, store, FakeCredentialStorage())
        account.restore()
        assertEquals(session.user, account.user.value)

        account.markSessionExpired()

        assertNull(account.user.value)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
    }
@Test
    fun `会话尚未注入网关时收到的登录失效不清空本地凭据`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val store = FakeSessionStore(stored = session)
        var attempts = 0
        val gateway = fakeGateway {
            attempts++
            throw RuntimeException("探活未完成")
        }
        val account = AccountManager({ gateway }, store, FakeCredentialStorage())

        account.restore()
        // 页面在注入完成前就吃到了「未登录」，于是判定会话失效
        account.markSessionExpired()

        // 注入失败只说明 Cookie 还没送到网关，不能据此删掉本地凭据
        assertEquals(0, store.clearCount)
        assertEquals(session, store.stored)
        assertEquals(session.user, account.user.value)
        // 判失效时要补一次注入，让后续重试自行恢复
        assertEquals(2, attempts)
    }

    @Test
    fun `网关被线路切换换掉后收到的登录失效同样不清凭据`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val store = FakeSessionStore(stored = session)
        var current = fakeGateway()
        val account = AccountManager({ current }, store, FakeCredentialStorage())
        account.restore()

        // 模拟切换线路后换了新的网关实例（新客户端里没有会话 Cookie）
        var reinjected = false
        current = fakeGateway { reinjected = true }
        account.markSessionExpired()

        assertEquals(0, store.clearCount)
        assertEquals(session, store.stored)
        assertEquals(true, reinjected)
    }
@Test
    fun `会话注入完成前等待信号不放行，注入结束后放行`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        val injectGate = CountDownLatch(1)
        val gateway = fakeGateway { injectGate.await() }
        val account = AccountManager({ gateway }, store, FakeCredentialStorage())

        val restoreJob = launch { account.restore() }
        // 用户态已经宣告为已登录，但 Cookie 还没进网关
        account.user.first { it != null }

        val leaked = withTimeoutOrNull(200) { account.awaitRestored(); true } ?: false
        assertFalse(leaked)

        injectGate.countDown()
        assertEquals(true, withTimeoutOrNull(5000) { account.awaitRestored(); true })
        restoreJob.join()
    }

    @Test
    fun `会话注入失败也放行等待者，让页面显示真实网络错误`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        val gateway = fakeGateway { throw RuntimeException("网络不可用") }
        val account = AccountManager({ gateway }, store, FakeCredentialStorage())

        account.restore()

        assertEquals(true, withTimeoutOrNull(5000) { account.awaitRestored(); true })
        assertEquals(session.user, account.user.value)
        assertEquals(0, store.clearCount)
    }
@Test
    fun `本地会话读取失败时等待信号仍然放行，页面不会永远停在加载中`() = runBlocking {
        val store = object : SessionStorage {
            override suspend fun load(): Jm3Session? = throw IllegalStateException("会话文件损坏")
            override suspend fun save(session: Jm3Session) = Unit
            override suspend fun clear() = Unit
            override suspend fun renewFailureCount(): Int = 0
            override suspend fun setRenewFailureCount(count: Int) = Unit
        }
        val account = AccountManager({ fakeGateway() }, store, FakeCredentialStorage())

        account.restore()

        assertEquals(true, withTimeoutOrNull(5000) { account.awaitRestored(); true })
        assertNull(account.user.value)
    }
@Test
    fun `线路切换换了网关后再次恢复会把会话注入新网关`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        var current = fakeGateway()
        val account = AccountManager({ current }, store, FakeCredentialStorage())
        account.restore()

        // 切线路换了网关实例：新客户端里没有 Cookie，重新恢复要把它补进去
        var reinjected = false
        current = fakeGateway { reinjected = true }
        account.restore()

        assertEquals(true, reinjected)
        assertEquals(session.user, account.user.value)
        assertEquals(0, store.clearCount)
    }
@Test
    fun `登录成功后保存凭据，登出后清除`() = runBlocking {
        val store = FakeSessionStore()
        val credentials = FakeCredentialStorage()
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val gateway = fakeGateway(onLogin = { _, _ -> session })
        val account = AccountManager({ gateway }, store, credentials)

        assertNull(account.login("isczjk", "secret"))
        assertEquals(SavedCredentials("isczjk", "secret"), credentials.stored)
        assertEquals(session.user, account.user.value)

        account.logout()
        assertNull(credentials.stored)
        assertEquals(1, credentials.clearCount)
    }

    @Test
    fun `冷启动本地无会话但有凭据时静默重登并恢复登录`() = runBlocking {
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "secret"))
        val store = FakeSessionStore(stored = null)
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val gateway = fakeGateway(
            onLogin = { username, password ->
                if (username == "isczjk" && password == "secret") session
                else throw RuntimeException("帳號或密碼錯誤")
            },
        )
        val account = AccountManager({ gateway }, store, credentials)

        account.restore()

        assertEquals(session.user, account.user.value)
        // 重登拿到的新会话要落盘，下次冷启动才不用再登一次
        assertEquals(session, store.stored)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `会话被判失效时先静默重登，成功后保持登录且不清凭据`() = runBlocking {
        val store = FakeSessionStore(stored = Jm3Session(user = testUser(), cookies = emptyList()))
        val fresh = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "renewed", "18comic.vip", "/")),
        )
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "secret"))
        val gateway = fakeGateway(onLogin = { _, _ -> fresh })
        val account = AccountManager({ gateway }, store, credentials)
        account.restore()

        account.markSessionExpired()

        assertEquals(fresh.user, account.user.value)
        assertEquals(0, store.clearCount)
        assertEquals(fresh, store.stored)
        assertEquals(0, credentials.clearCount)
    }

    @Test
    fun `会话失效且静默重登失败时清会话回落未登录`() = runBlocking {
        val store = FakeSessionStore(stored = Jm3Session(user = testUser(), cookies = emptyList()))
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "expired"))
        val gateway = fakeGateway(onLogin = { _, _ -> throw RuntimeException("帳號或密碼錯誤") })
        val account = AccountManager({ gateway }, store, credentials)
        account.restore()

        account.markSessionExpired()

        assertNull(account.user.value)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
    }

    @Test
    fun `冷启动静默重登连续失败达到阈值后清掉保存的凭据`() = runBlocking {
        val store = FakeSessionStore(stored = null)
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "expired"))
        // 每次冷启动都是新的 AccountManager 与新网关实例，失败次数只能靠落盘累计
        val failing = { fakeGateway(onLogin = { _, _ -> throw RuntimeException("帳號或密碼錯誤") }) }

        AccountManager(failing, store, credentials).restore()
        assertEquals(1, store.renewFailures)
        AccountManager(failing, store, credentials).restore()
        assertEquals(2, store.renewFailures)
        AccountManager(failing, store, credentials).restore()

        // 第三次仍然失败：密码已经改了，再留着只会让每次冷启动都白跑一次登录
        assertNull(credentials.stored)
        assertEquals(1, credentials.clearCount)
        assertEquals(0, store.renewFailures)
    }

    @Test
    fun `静默重登连续失败未达阈值时保留凭据`() = runBlocking {
        val store = FakeSessionStore(stored = null)
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "secret"))
        val failing = { fakeGateway(onLogin = { _, _ -> throw RuntimeException("網路逾時") }) }

        AccountManager(failing, store, credentials).restore()
        AccountManager(failing, store, credentials).restore()

        // 两次失败可能只是网络问题，凭据还得留着给下次冷启动用
        assertEquals(2, store.renewFailures)
        assertEquals(SavedCredentials("isczjk", "secret"), credentials.stored)
        assertEquals(0, credentials.clearCount)
    }

    @Test
    fun `静默重登中途成功一次就把失败计数归零`() = runBlocking {
        val store = FakeSessionStore(stored = null)
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "secret"))
        val failing = { fakeGateway(onLogin = { _, _ -> throw RuntimeException("網路逾時") }) }
        AccountManager(failing, store, credentials).restore()
        assertEquals(1, store.renewFailures)

        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val recovered = AccountManager({ fakeGateway(onLogin = { _, _ -> session }) }, store, credentials)
        recovered.restore()

        assertEquals(session.user, recovered.user.value)
        assertEquals(0, store.renewFailures)

        // 重登成功后会话会落盘；这里模拟它之后又被服务端判失效清掉，冷启动只剩凭据可试
        store.stored = null

        // 归零之后重新攒：再失败两次仍不到阈值，凭据还得留着
        AccountManager(failing, store, credentials).restore()
        AccountManager(failing, store, credentials).restore()
        assertEquals(2, store.renewFailures)
        assertEquals(SavedCredentials("isczjk", "secret"), credentials.stored)
    }

    @Test
    fun `恢复本地会话后把保存的凭据交给网关，让库内自动重登生效`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val primed = mutableListOf<Pair<String, String>>()
        val gateway = fakeGateway(onPrimeCredentials = { saved -> primed += saved.username to saved.password })

        account(
            store = FakeSessionStore(stored = session),
            credentials = FakeCredentialStorage(SavedCredentials("test_user", "secret")),
            gateway = { gateway },
        ).restore()

        assertEquals(listOf("test_user" to "secret"), primed)
    }

    @Test
    fun `注入会话落在调用方已经判定过的那个网关实例上`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        var firstInjections = 0
        var secondInjections = 0
        val judged = fakeGateway(onRestoreSession = { firstInjections++ })
        val stray = fakeGateway(onRestoreSession = { secondInjections++ })
        var calls = 0
        // 每次取网关都给一个新实例：注入若自己去要网关，拿到的就不是 restore() 开头判定的那个
        val account = AccountManager(
            gatewayProvider = { if (calls++ == 0) judged else stray },
            store = store,
            credentials = FakeCredentialStorage(),
        )

        account.restore()

        assertEquals(1, firstInjections)
        assertEquals(0, secondInjections)
    }

    @Test
    fun `线路切换补注入会话时把凭据交给新网关`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        val primed = mutableListOf<Pair<String, String>>()
        var gateway = fakeGateway()
        val replacement = fakeGateway(onPrimeCredentials = { saved -> primed += saved.username to saved.password })
        val account = account(
            store = store,
            credentials = FakeCredentialStorage(SavedCredentials("test_user", "secret")),
            gateway = { gateway },
        )
        account.restore()

        gateway = replacement
        account.markSessionExpired()

        assertEquals(listOf("test_user" to "secret"), primed)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `登录成功后把会话写进本地存储`() = runBlocking {
        val store = FakeSessionStore()
        val gateway = fakeGateway(onLogin = { username, _ -> Jm3Session(user = testUser().copy(username = username), cookies = emptyList()) })
        val account = account(store = store, gateway = { gateway })

        account.login("test_user", "secret")

        assertEquals("test_user", store.stored?.user?.username)
    }

    @Test
    fun `退到后台时把库内刷新过的会话 Cookie 回写本地存储`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "stale", "18comic.vip", "/")),
        )
        val renewed = listOf(CookieSnapshot("AVS", "renewed", "18comic.vip", "/"))
        val store = FakeSessionStore(stored = session)
        val gateway = fakeGateway(onCurrentCookies = { renewed })
        val account = account(store = store, gateway = { gateway })
        account.restore()

        account.persistSession()

        // 库在请求内自己重登换了 Cookie：本地旧快照必须跟着换，否则下次冷启动白登一次
        assertEquals(renewed, store.stored?.cookies)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `当前网关没拿到会话时后台回写不改动本地存储`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "stale", "18comic.vip", "/")),
        )
        val store = FakeSessionStore(stored = session)
        var current = fakeGateway()
        val account = account(store = store, gateway = { current })
        account.restore()

        // 切线路换了新网关：它手里的 Cookie 与本地会话无关，不能拿来覆盖
        current = fakeGateway(onCurrentCookies = { listOf(CookieSnapshot("AVS", "other", "18comic.vip", "/")) })
        account.persistSession()

        assertEquals(session, store.stored)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `库内 Cookie 与本地一致时不重复写盘`() = runBlocking {
        val session = Jm3Session(
            user = testUser(),
            cookies = listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")),
        )
        val store = FakeSessionStore(stored = session)
        val gateway = fakeGateway(onCurrentCookies = { session.cookies })
        val account = account(store = store, gateway = { gateway })
        account.restore()

        account.persistSession()

        assertEquals(0, store.saveCount)
    }

    @Test
    fun `未登录时后台回写不写盘`() = runBlocking {
        val store = FakeSessionStore(stored = null)
        val gateway = fakeGateway(onCurrentCookies = { listOf(CookieSnapshot("AVS", "token", "18comic.vip", "/")) })
        val account = account(store = store, gateway = { gateway })

        account.persistSession()

        assertEquals(0, store.saveCount)
        assertNull(store.stored)
    }
    @Test
    fun `同一网关重复恢复只注入一次，不重复搬凭据`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        var injected = 0
        val primed = mutableListOf<String>()
        val gateway = fakeGateway(
            onRestoreSession = { injected++ },
            onPrimeCredentials = { primed += it.username },
        )
        val account = account(
            store = FakeSessionStore(stored = session),
            credentials = FakeCredentialStorage(SavedCredentials("test_user", "secret")),
            gateway = { gateway },
        )

        account.restore()
        account.restore()

        // 启动与设置页保存都会调 restore：已注入过的网关不能再搬一遍 Cookie 与密码
        assertEquals(1, injected)
        assertEquals(1, primed.size)
    }

    @Test
    fun `多个请求同时撞上会话失效时只重登一次`() = runBlocking {
        val store = FakeSessionStore(stored = Jm3Session(user = testUser(), cookies = emptyList()))
        val credentials = FakeCredentialStorage(SavedCredentials("isczjk", "secret"))
        val gate = CountDownLatch(1)
        var loginCount = 0
        val fresh = Jm3Session(user = testUser(), cookies = emptyList())
        val gateway = fakeGateway(
            onLogin = { _, _ ->
                loginCount++
                // 让第一个请求在登录里停住，后两个请求必然在锁上排队
                gate.await()
                fresh
            },
        )
        val account = account(store = store, credentials = credentials, gateway = { gateway })
        account.restore()

        val expirations = List(3) { launch { account.markSessionExpired() } }
        gate.countDown()
        expirations.forEach { it.join() }

        // 三个请求拿到的是同一个失效结论，但只该换一次会话
        assertEquals(1, loginCount)
        assertEquals(fresh.user, account.user.value)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `登出时连同网关内存里的密码一起清掉`() = runBlocking {
        var forgotten = 0
        val gateway = fakeGateway(
            onLogin = { _, _ -> Jm3Session(user = testUser(), cookies = emptyList()) },
            onForgetCredentials = { forgotten++ },
        )
        val account = account(store = FakeSessionStore(), gateway = { gateway })
        account.login("isczjk", "secret")

        account.logout()

        // 用户主动登出后，客户端实例里不能再留着能自动重登的密码
        assertEquals(1, forgotten)
        assertNull(account.user.value)
    }
}
