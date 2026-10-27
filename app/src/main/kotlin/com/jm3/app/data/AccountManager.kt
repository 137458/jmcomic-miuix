package com.jm3.app.data

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
    private val store: SessionStore,
) {

    private val _user = MutableStateFlow<Jm3User?>(null)

    /** 当前登录用户；null 表示未登录。 */
    val user: StateFlow<Jm3User?> = _user.asStateFlow()

    /** 启动时恢复本地会话并注回网关；会话失效则清空持久化，保持未登录。 */
    suspend fun restore() {
        if (_user.value != null) return
        val session = store.load() ?: return
        val restored = withContext(Dispatchers.IO) {
            runCatching { gatewayProvider().restoreSession(session) }.isSuccess
        }
        if (restored) {
            _user.value = session.user
        } else {
            // 会话无法注入时不能让 UI 显示已登录，否则后续请求全部失败
            store.clear()
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
}
