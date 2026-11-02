package com.jm3.app.miuix.data

import com.jm3.core.Jm3Gateway
import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun fakeGateway(onRestoreSession: (Jm3Session) -> Unit = {}): Jm3Gateway {
        return Proxy.newProxyInstance(
            Jm3Gateway::class.java.classLoader,
            arrayOf(Jm3Gateway::class.java),
        ) { _, method, args ->
            when (method.name) {
                "restoreSession" -> {
                    onRestoreSession(args[0] as Jm3Session)
                    null
                }
                "logout" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Jm3Gateway
    }

    private class FakeSessionStore(var stored: Jm3Session? = null) : SessionStorage {
        var clearCount = 0
        override suspend fun load(): Jm3Session? = stored
        override suspend fun save(session: Jm3Session) { stored = session }
        override suspend fun clear() {
            stored = null
            clearCount++
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
        val account = AccountManager({ gateway }, store)

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
        val account = AccountManager({ fakeGateway() }, store)

        account.restore()

        assertNull(account.user.value)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `markSessionExpired 清除存储并将用户置空`() = runBlocking {
        val session = Jm3Session(user = testUser(), cookies = emptyList())
        val store = FakeSessionStore(stored = session)
        val gateway = fakeGateway()
        val account = AccountManager({ gateway }, store)
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
        val account = AccountManager({ gateway }, store)

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
        val account = AccountManager({ current }, store)
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
        val account = AccountManager({ gateway }, store)

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
        val account = AccountManager({ gateway }, store)

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
        }
        val account = AccountManager({ fakeGateway() }, store)

        account.restore()

        assertEquals(true, withTimeoutOrNull(5000) { account.awaitRestored(); true })
        assertNull(account.user.value)
    }
}
