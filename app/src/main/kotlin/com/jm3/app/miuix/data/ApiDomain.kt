package com.jm3.app.miuix.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jm3.core.GatewayFactory
import com.jm3.core.Jm3Gateway
import com.jm3.core.settings.Domains
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 线路域：当前网关实例、自定义线路切换、线路池落盘。
 *
 * 网关是「一处实例、多处读」的东西：线路切换后要让所有调用方读到新实例，所以覆盖态用 Compose 状态持有，
 * 组合内读取即可感知重组。会话迁移不在这里做——那需要 [com.jm3.core.session.AccountManager]，
 * 由调用方通过 [applyDomains] 的 onSwitched 传进来，线路域因此不认识账号。
 */
class ApiDomain(context: Context, private val appScope: CoroutineScope) {

    private val appContext: Context = context.applicationContext

    /** 线路池缓存文件：网关装配早于首屏请求，所以启动时同步读一次，见 [warmUp]。 */
    private val domainPoolCache = FileDomainPoolCache(File(appContext.filesDir, "api_domain_pool.txt"))

    /** 线路切换后的网关实例；组合内读取可感知切换并重组。 */
    private var gatewayOverride by mutableStateOf<Jm3Gateway?>(null)

    /** 上次刷新落盘的线路池：网关装配在首屏请求之前同步执行，所以启动时先读一次。 */
    @Volatile
    private var cachedDomainPool: List<String> = emptyList()

    private val defaultGateway: Jm3Gateway by lazy {
        GatewayFactory.create(emptyList(), cachedDomainPool, ::rememberDomainPool)
    }

    /** 当前生效网关：线路覆盖优先，否则库默认解析。 */
    internal val gateway: Jm3Gateway
        get() = gatewayOverride ?: defaultGateway

    /** 已生效的自定义线路（空表 = 库默认解析）。重建网关等于让库重跑一遍初始化，线路没变就必须跳过。 */
    @Volatile
    private var appliedDomains: List<String> = emptyList()

    /** 线路切换串行化：启动恢复与设置页保存可能并发调用，重建网关不能交叉执行。 */
    private val domainSwitchLock = Mutex()

    /**
     * 应用自定义 API 线路：重建网关，随后由调用方把已持久化会话迁移过去。
     *
     * @param onSwitched 网关已换成功后的会话迁移动作（走网络与写盘，必须在锁外执行）。
     */
    suspend fun applyDomains(apiDomains: List<String>, onSwitched: suspend () -> Unit) {
        var previous: Jm3Gateway? = null
        val switched = domainSwitchLock.withLock {
            val requested = Domains.normalize(apiDomains)
            // 启动时默认就是空表且未切过线路：直接沿用 defaultGateway，不再多建一个网关。
            if (requested == appliedDomains) return@withLock false
            // 旧网关的 HTTP 客户端持有连接池，切线路后不再可达：迁移完会话即主动释放。
            previous = gatewayOverride
            appliedDomains = requested
            gatewayOverride =
                if (requested.isEmpty()) null
                else GatewayFactory.create(requested, cachedDomainPool, ::rememberDomainPool)
            true
        }
        if (!switched) return
        // 会话注入要走网络与写盘，放到锁外：锁只保护网关实例本身的重建，
        // 否则一次线路切换会把并发的其它切换全堵在网络超时上。
        onSwitched()
        previous?.let { old -> runCatching { old.close() } }
    }

    /** 冷启动预热：线路池一次读盘，避免网关装配时还没拿到上次刷新结果。 */
    internal fun warmUp() {
        cachedDomainPool = domainPoolCache.read()
    }

    /** 信箱未读数的网络读数：怎么用（归零/保留）由调用方决定，见 [com.jm3.core.session.UnreadBadge]。 */
    internal suspend fun unreadCount(): Int = gateway.notifications().items.count { !it.read }

    /** 刷新出的线路池落盘，下次冷启动直接预置；写盘失败只影响下次启动的预热。 */
    private fun rememberDomainPool(pool: List<String>) {
        if (pool.isEmpty()) return
        cachedDomainPool = pool
        appScope.launch { runCatching { domainPoolCache.write(pool) } }
    }
}
