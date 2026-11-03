package com.jm3.app.miuix.data

import com.jm3.core.CredentialStorage
import com.jm3.core.SessionAuth
import com.jm3.core.SavedCredentials
import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import com.jm3.core.runCatchingSuspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 账号状态：登录/登出/启动恢复，向 UI 暴露只读 StateFlow。
 *
 * [gatewayProvider] 必须在同一条线路下返回同一个网关实例：这里靠实例身份判断当前网关是否已拿到会话 Cookie，
 * 换了实例即视为需要重新注入。
 */
class AccountManager(
    private val gatewayProvider: () -> SessionAuth,
    private val store: SessionStorage,
    private val credentials: CredentialStorage,
) {

    private val _user = MutableStateFlow<Jm3User?>(null)

    /** 当前登录用户；null 表示未登录。 */
    val user: StateFlow<Jm3User?> = _user.asStateFlow()

    /** 启动恢复是否已尝试完毕：成功、失败都置 true，页面据此决定何时可以发鉴权请求。 */
    private val _restored = MutableStateFlow(false)

    /** 已把会话 Cookie 注入进去的网关实例；线路切换换了网关后自然失效。 */
    @Volatile
    private var injectedGateway: SessionAuth? = null

    /** 静默重登已经试过的网关实例：同一实例只试一次，换线路换了网关才允许再试。 */
    @Volatile
    private var renewedGateway: SessionAuth? = null

    /** 会话代次：每落地一次登录会话自增。并发续期靠它判断「别人已经续上了，不必再登一次」。 */
    @Volatile
    private var sessionEpoch = 0

    /** 会话存储的读改写锁：后台回写与登出/登录落地不能交叉写同一个键。 */
    private val storeLock = Mutex()

    /** 静默重登串行化：多个请求同时撞上失效时只登一次。 */
    private val renewLock = Mutex()

    /**
     * 恢复本地会话并注入当前网关；本地存在会话时立即恢复登录状态，不因瞬时网络或探活异常删除凭据。
     *
     * 冷启动调一次；线路切换换了网关实例后再调一次，Cookie 才会跟着迁到新客户端。
     * 已注入当前网关时是空操作。本地没有会话（上次掉登录或用户从别的入口退出）但有保存的凭据时，
     * 这里直接静默重登：用户重启应用后仍是登录态，不必再输一次密码。
     */
    suspend fun restore() {
        val gateway = gatewayProvider()
        if (_restored.value && injectedGateway === gateway) return
        try {
            val session = loadSession()
            if (session != null) {
                if (_user.value == null) _user.value = session.user
                if (injectedGateway !== gateway) inject(session)
            } else if (renewedGateway !== gateway) {
                renewSession()
            }
        } finally {
            // 无论成功失败都必须放行等待者，否则读取异常会让所有鉴权页面永久停在加载中
            _restored.value = true
        }
    }

    /**
     * 等待启动恢复尝试结束再发鉴权请求。
     *
     * [restore] 会先置用户态再注入 Cookie，页面在用户态一变就发请求时，服务端看到的还是未登录，
     * 于是把一次启动竞态误判成会话失效。注入失败也放行：此时页面应该显示真实的网络错误。
     */
    suspend fun awaitRestored() {
        _restored.first { it }
    }

    /**
     * 把网关当前持有的会话 Cookie 回写本地存储。
     *
     * 库在请求内自动重登后会刷新自己的 cookieJar，而本地存的是上一份快照；不回写的话下次冷启动
     * 注入的还是旧 Cookie，白跑一次登录。应用退到后台时调用，读 Cookie 与落盘都在 IO 上。
     */
    suspend fun persistSession() {
        val user = _user.value ?: return
        val gateway = gatewayProvider()
        // 当前网关没拿到会话：它手里的 Cookie 与本地会话无关（未登录，或切线路后还没注入）
        if (injectedGateway !== gateway) return
        withContext(Dispatchers.IO) {
            val cookies = runCatchingSuspending { gateway.currentCookies() }.getOrNull().orEmpty()
            if (cookies.isEmpty()) return@withContext
            storeLock.withLock { rewriteSession(user, gateway, cookies) }
        }
    }

    /**
     * 比较并回写会话，全程在 [storeLock] 内完成。
     *
     * 比较与写入之间可能插入一次登出：锁内再确认用户态与网关没变，否则会把刚清掉的会话又写回去。
     */
    private suspend fun rewriteSession(
        user: Jm3User,
        gateway: SessionAuth,
        cookies: List<CookieSnapshot>,
    ) {
        if (_user.value != user || injectedGateway !== gateway) return
        val stored = loadSession()
        // 没变就别写：DataStore 每次落盘都要串行写文件，退到后台不该无谓写
        if (stored != null && stored.user == user && sameCookies(stored.cookies, cookies)) return
        runCatchingSuspending { store.save(Jm3Session(user = user, cookies = cookies)) }
    }

    /** Cookie 的先后顺序取决于客户端内部存储，比较时忽略顺序。 */
    private fun sameCookies(left: List<CookieSnapshot>, right: List<CookieSnapshot>): Boolean =
        left.size == right.size && left.toSet() == right.toSet()

    /** 登录成功返回 null、持久化会话与凭据；失败返回错误消息。 */
    suspend fun login(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val gateway = gatewayProvider()
            runCatchingSuspending { gateway.login(username, password) }
                .onSuccess { session ->
                    adopt(session, gateway)
                    // 留着密码才能在上面的内存加密失效后静默重登；加密交给 Keystore，落盘只有密文
                    runCatchingSuspending { credentials.save(SavedCredentials(username, password)) }
                }
                .exceptionOrNull()?.message
        }

    suspend fun logout() {
        val gateway = gatewayProvider()
        withContext(Dispatchers.IO) {
            runCatchingSuspending { gateway.logout() }
            // 用户主动登出就是不想再自动登录：凭据与网关内存里的密码一起清掉
            runCatchingSuspending { credentials.clear() }
            gateway.forgetCredentials()
            storeLock.withLock { runCatchingSuspending { store.clear() } }
        }
        _user.value = null
        injectedGateway = null
    }

    /**
     * 服务端已判定会话失效时由 UI 调用：清掉持久化会话并把当前用户置空。
     *
     * 与 [logout] 的唯一区别是不再请求网关登出——会话在服务端已经作废，再发一次只会再吃一个 401。
     * 置空用户是必须的：否则界面仍按已登录渲染，后续每个请求都会重复撞上同一个 401。
     *
     * 但传话人未必可信：当前网关里根本没有注入过会话时（启动注入未完成、或线路切换换了新网关），
     * 这个「未登录」只能说明 Cookie 还没送到，此时补一次注入即可恢复，绝不能删掉本地凭据。
     *
     * 会话确实作废时也不急着把用户踢下线：凭据还在就先静默重登，重登不上（密码改了、账号受限、
     * 网络不通）才真的清会话。
     */
    suspend fun markSessionExpired() {
        val gateway = gatewayProvider()
        if (injectedGateway !== gateway) {
            loadSession()?.let { inject(it) }
            return
        }
        if (renewSession()) return
        storeLock.withLock { runCatchingSuspending { store.clear() } }
        _user.value = null
        injectedGateway = null
    }

    /**
     * 用本地保存的凭据静默重登；成功即刷新会话与用户态。
     *
     * 多个请求同时撞上失效时只登一次：拿锁前记下会话代次，抢到锁后发现已经有人续期过就直接认账。
     */
    private suspend fun renewSession(): Boolean {
        val epochBefore = sessionEpoch
        return renewLock.withLock {
            if (sessionEpoch != epochBefore) return@withLock true
            val saved = loadCredentials() ?: return@withLock false
            val gateway = gatewayProvider()
            renewedGateway = gateway
            withContext(Dispatchers.IO) {
                runCatchingSuspending { gateway.login(saved.username, saved.password) }
            }.onSuccess { session -> adopt(session, gateway) }.isSuccess
        }
    }

    /** 登录态落地：会话落盘、用户态刷新、当前网关视为已注入。 */
    private suspend fun adopt(session: Jm3Session, gateway: SessionAuth) {
        _user.value = session.user
        injectedGateway = gateway
        _restored.value = true
        sessionEpoch++
        // 会话落盘后冷启动优先用 Cookie 恢复，不必每次都拿密码去登录一次
        storeLock.withLock { runCatchingSuspending { store.save(session) } }
    }

    /**
     * 把会话注入网关，并把本地凭据一并交给它，让库内自动重登可用。
     *
     * 库只在内存里还留着加密密码时才会在会话失效后自行重登并重放请求（JmApiClient.shouldRefreshLogin），
     * 而那段内存每次冷启动必为空；这里补一次，一次过期就不会直接落到页面上。
     */
    private suspend fun inject(session: Jm3Session) {
        val gateway = gatewayProvider()
        withContext(Dispatchers.IO) {
            runCatchingSuspending { gateway.restoreSession(session) }.onSuccess {
                injectedGateway = gateway
                // 凭据解密要走 Keystore，放到 IO 上做，别卡住发起恢复的界面线程
                val saved = loadCredentials() ?: return@onSuccess
                runCatchingSuspending { gateway.primeCredentials(saved) }
            }
        }
    }

    private suspend fun loadSession(): Jm3Session? =
        runCatchingSuspending { store.load() }.getOrNull()

    private suspend fun loadCredentials(): SavedCredentials? =
        runCatchingSuspending { credentials.load() }.getOrNull()
}
