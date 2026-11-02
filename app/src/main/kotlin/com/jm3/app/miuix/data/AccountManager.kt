package com.jm3.app.miuix.data

import com.jm3.core.Jm3Gateway
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 账号状态：登录/登出/启动恢复，向 UI 暴露只读 StateFlow。
 *
 * [gatewayProvider] 必须在同一条线路下返回同一个网关实例：这里靠实例身份判断当前网关是否已拿到会话 Cookie，
 * 换了实例即视为需要重新注入。
 */
class AccountManager(
    private val gatewayProvider: () -> Jm3Gateway,
    private val store: SessionStorage,
) {

    private val _user = MutableStateFlow<Jm3User?>(null)

    /** 当前登录用户；null 表示未登录。 */
    val user: StateFlow<Jm3User?> = _user.asStateFlow()

    /** 启动恢复是否已尝试完毕：成功、失败都置 true，页面据此决定何时可以发鉴权请求。 */
    private val _restored = MutableStateFlow(false)

    /** 已把会话 Cookie 注入进去的网关实例；线路切换换了网关后自然失效。 */
    @Volatile
    private var injectedGateway: Jm3Gateway? = null

    /**
     * 恢复本地会话并注入当前网关；本地存在会话时立即恢复登录状态，不因瞬时网络或探活异常删除凭据。
     *
     * 冷启动调一次；线路切换换了网关实例后再调一次，Cookie 才会跟着迁到新客户端。已注入当前网关时是空操作。
     */
    suspend fun restore() {
        val gateway = gatewayProvider()
        if (_restored.value && injectedGateway === gateway) return
        // 无论成功失败都必须放行等待者，否则读取异常会让所有鉴权页面永久停在加载中
        try {
            val session = runCatching { store.load() }.getOrNull() ?: return
            if (!_restored.value) _user.value = session.user
            if (injectedGateway !== gateway) inject(session)
        } finally {
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

    /** 登录成功返回 null 并持久化会话；失败返回错误消息。 */
    suspend fun login(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val gateway = gatewayProvider()
            runCatching { gateway.login(username, password) }
                .onSuccess { session ->
                    store.save(session)
                    _user.value = session.user
                    injectedGateway = gateway
                    _restored.value = true
                }
                .exceptionOrNull()?.message
        }

    suspend fun logout() {
        withContext(Dispatchers.IO) { runCatching { gatewayProvider().logout() } }
        store.clear()
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
     */
    suspend fun markSessionExpired() {
        val gateway = gatewayProvider()
        if (injectedGateway !== gateway) {
            store.load()?.let { inject(it) }
            return
        }
        store.clear()
        _user.value = null
        injectedGateway = null
    }

    private suspend fun inject(session: Jm3Session) {
        val gateway = gatewayProvider()
        withContext(Dispatchers.IO) {
            runCatching { gateway.restoreSession(session) }
                .onSuccess { injectedGateway = gateway }
        }
    }
}
