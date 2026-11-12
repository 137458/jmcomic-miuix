package com.jm3.app.miuix.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.jm3.core.GatewayFactory
import com.jm3.core.Jm3Gateway
import com.jm3.core.settings.Domains
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 线路域：当前网关实例、自定义线路切换、线路池落盘。
 *
 * 网关是「一处实例、多处读」的东西：线路切换后要让所有调用方读到新实例，所以覆盖态用 Compose 状态持有，
 * 组合内读取即可感知重组。会话迁移不在这里做——那需要 [com.jm3.core.session.AccountManager]，
 * 由调用方通过 [applyDomains] 的 onSwitched 传进来，线路域因此不认识账号。
 */
class ApiDomain internal constructor(
    context: Context,
    private val appScope: CoroutineScope,
    private val gatewayFactory: (List<String>, List<String>, (List<String>) -> Unit) -> Jm3Gateway,
) {

    constructor(context: Context, appScope: CoroutineScope) : this(
        context = context,
        appScope = appScope,
        gatewayFactory = GatewayFactory::create,
    )

    private val appContext: Context = context.applicationContext

    /** 线路池缓存文件：网关装配早于首屏请求，所以启动时同步读一次，见 [warmUp]。 */
    private val domainPoolCache = FileDomainPoolCache(File(appContext.filesDir, "api_domain_pool.txt"))

    private val defaultGatewayLock = Any()

    /** 冷启动未走过 applyDomains 时惰性创建的默认网关；切线或切回默认时统一纳入关闭与重建生命周期。 */
    @Volatile
    private var fallbackGateway: Jm3Gateway? = null

    /** 线路切换后的活跃网关实例；组合内读取可感知切换并重组。 */
    private var gatewayOverride by mutableStateOf<Jm3Gateway?>(null)

    /** 上次刷新落盘的线路池：网关装配在首屏请求之前同步执行，所以启动时先读一次。 */
    @Volatile
    private var cachedDomainPool: List<String> = emptyList()

    /** 当前生效网关：线路覆盖优先，否则惰性创建默认网关。 */
    internal val gateway: Jm3Gateway
        get() = gatewayOverride ?: synchronized(defaultGatewayLock) {
            gatewayOverride ?: fallbackGateway ?: gatewayFactory(
                emptyList(),
                cachedDomainPool,
                ::rememberDomainPool,
            ).also { fallbackGateway = it }
        }

    /** 已生效的自定义线路（空表 = 库默认解析）。重建网关等于让库重跑一遍初始化，线路没变就必须跳过。 */
    @Volatile
    private var appliedDomains: List<String> = emptyList()

    /** 线路切换串行化：网关重建、快照更新、会话迁移与旧网关关闭整体串行，防止并发切换提前关掉仍在使用的网关。 */
    private val domainSwitchLock = Mutex()

    /**
     * 应用自定义 API 线路：在 IO 线程重建网关并安全提交快照，随后串行执行会话迁移并释放旧网关。
     *
     * @param onSwitched 网关已换成功后的会话迁移动作。
     */
    suspend fun applyDomains(apiDomains: List<String>, onSwitched: suspend () -> Unit) {
        domainSwitchLock.withLock {
            val requested = Domains.normalize(apiDomains)
            // 启动时默认就是空表且未切过线路：直接沿用已初始化的默认网关，不再多建一个网关。
            if (requested == appliedDomains) return@withLock
            val next = withContext(Dispatchers.IO) {
                gatewayFactory(requested, cachedDomainPool, ::rememberDomainPool)
            }
            val previous = synchronized(defaultGatewayLock) {
                val active = gatewayOverride ?: fallbackGateway
                fallbackGateway = null
                appliedDomains = requested
                Snapshot.withMutableSnapshot {
                    gatewayOverride = next
                }
                active
            }
            // 旧网关在迁移完成后再于 IO 线程释放（异常/取消路径同样释放），避免未迁移完就被关闭或阻塞主线程
            try {
                onSwitched()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    previous?.let { old -> runCatching { old.close() } }
                }
            }
        }
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
