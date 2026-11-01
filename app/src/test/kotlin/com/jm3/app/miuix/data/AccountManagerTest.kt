package com.jm3.app.miuix.data

import com.jm3.core.Jm3Gateway
import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val account = AccountManager({ fakeGateway() }, store)
        account.restore()
        assertEquals(session.user, account.user.value)

        account.markSessionExpired()

        assertNull(account.user.value)
        assertEquals(1, store.clearCount)
        assertNull(store.stored)
    }
}
