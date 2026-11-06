package com.jm3.app.miuix.data

import android.content.Context
import com.jm3.core.CredentialStorage
import com.jm3.core.EncryptedCredentialStore
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.session.UnreadBadge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 会话域：账号、带闸门的调用入口、未读数。
 *
 * 三者必须共用同一个 [AccountManager] 实例——闸门状态、静默重登、会话失效都挂在它身上；
 * 各自 new 一个就是同一进程两个入口，可测性上的假接缝。所以这里把它们绑在一个 holder 里出口化。
 */
class SessionDomain(
    context: Context,
    settingsStore: SettingsStore,
    private val api: ApiDomain,
    private val appScope: CoroutineScope,
) {

    private val appContext: Context = context.applicationContext

    /** 登录凭据：加密交给 Keystore，密文与设置同库落盘。 */
    private val credentials: CredentialStorage by lazy {
        EncryptedCredentialStore(settingsStore, KeystoreCredentialCipher())
    }

    /** 当前登录用户与会话恢复。 */
    val account: AccountManager by lazy {
        AccountManager({ api.gateway }, SessionStore(appContext), credentials)
    }

    /** 需要登录态的网关调用的唯一入口：屏幕通过形参接收它，不各自等闸门/切 IO/判失效。 */
    val authCall: AuthCall by lazy { AuthCall(account) }

    /** 信箱未读数：登录后刷新，供底栏「我的」角标与我的页入口共用。 */
    // 未读数也是网关调用：走 [AuthCall] 才有启动闸门与「401 判失效」，并且切到 IO——
    // 库的 notifications() 是阻塞方法，直接在这里调就是主线程联网。
    val unread: UnreadBadge = UnreadBadge { authCall.call { api.unreadCount() } }

    /** 重新统计未读：登录态由账号决定，调用方不必自己判；未登录归零、请求失败保留上次结果由 [UnreadBadge] 决定。 */
    suspend fun refreshUnread() {
        unread.refresh(account.user.value != null)
    }

    /** 后台刷一次未读：角标刷新不需要调用方等结果。 */
    fun scheduleRefreshUnread() {
        appScope.launch { refreshUnread() }
    }

    /** 换线路并把已持久化会话迁移过去：网关重建在 [ApiDomain]，会话迁移归这个域（它持有账号）。 */
    suspend fun applyDomains(apiDomains: List<String>) {
        api.applyDomains(apiDomains) { account.restore() }
    }
}
