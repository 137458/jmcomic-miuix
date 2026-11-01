package com.jm3.app.miuix.data

import com.jm3.core.Jm3Gateway
import com.jm3.core.model.Jm3User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** 账号状态：登录/登出/启动恢复，向 UI 暴露只读 StateFlow。 */
class AccountManager(
    private val gatewayProvider: () -> Jm3Gateway,
    private val store: SessionStorage,
) {

    private val _user = MutableStateFlow<Jm3User?>(null)

    /** 当前登录用户；null 表示未登录。 */
    val user: StateFlow<Jm3User?> = _user.asStateFlow()

    /** 启动时恢复本地会话并注回网关；本地存在会话时立即恢复登录状态，不因瞬时网络或探活异常删除凭据。 */
    suspend fun restore() {
        if (_user.value != null) return
        val session = store.load() ?: return
        _user.value = session.user
        withContext(Dispatchers.IO) {
            runCatching { gatewayProvider().restoreSession(session) }
        }
    }

    /** 登录成功返回 null 并持久化会话；失败返回错误消息。 */
    suspend fun login(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { gatewayProvider().login(username, password) }
                .onSuccess { session ->
                    store.save(session)
                    _user.value = session.user
                }
                .exceptionOrNull()?.message
        }

    suspend fun logout() {
        withContext(Dispatchers.IO) { runCatching { gatewayProvider().logout() } }
        store.clear()
        _user.value = null
    }

    /**
     * 服务端已判定会话失效时由 UI 调用：清掉持久化会话并把当前用户置空。
     *
     * 与 [logout] 的唯一区别是不再请求网关登出——会话在服务端已经作废，再发一次只会再吃一个 401。
     * 置空用户是必须的：否则界面仍按已登录渲染，后续每个请求都会重复撞上同一个 401。
     */
    suspend fun markSessionExpired() {
        store.clear()
        _user.value = null
    }
}
