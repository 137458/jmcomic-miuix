package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.net.provider.DomainProbe
import io.github.jukomu.jmcomic.core.net.provider.JmDomainManager
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 启动域名池：自定义线路优先，其次是上次刷新落盘的池；跨组去重保序。
 *
 * 顺序即优先级：库的 getBestDomain 取失败计数最小的域名，并列时取池中靠前者。
 * 两组都为空（首次安装且没填自定义线路）返回空表，由调用方回退到库自己的初始化链。
 */
internal fun startupDomainPool(custom: List<String>, cached: List<String>): List<String> =
    Domains.normalize(custom + cached)

/** 库没有公开替换域名池的入口，只能读私有字段；字段名变更时惰性失败而非拖垮类初始化。 */
private val domainManagerField: Field by lazy {
    AbstractJmClient::class.java.getDeclaredField("domainManager").apply { isAccessible = true }
}

internal fun domainManagerOf(client: AbstractJmClient): JmDomainManager =
    domainManagerField.get(client) as JmDomainManager

/**
 * 库只在 protected 的 createDomainProbe 上暴露探活器；成员名变更时降级为「不刷新线路池」并留一条事实日志。
 *
 * 惰性初始化只尝试一次：取不到就缓存 null，后续刷新直接跳过，不会反复反射也不会重复刷日志。
 */
private val domainProbeMethod: Method? by lazy {
    runCatching {
        AbstractJmClient::class.java.getDeclaredMethod("createDomainProbe").apply { isAccessible = true }
    }.getOrElse { failure ->
        Jm3Log.warn(TAG, "库内反射成员 createDomainProbe 取不到（" + failure.javaClass.name + "），线路池刷新降级")
        null
    }
}

/** 探活器取不到时返回 null，由调用方跳过整段刷新。 */
private fun domainProbeOf(client: AbstractJmClient): DomainProbe? =
    domainProbeMethod?.invoke(client) as? DomainProbe

/** 首屏之后再补跑线路表刷新的延迟：留出首屏请求的窗口，避免探活抢首屏带宽。 */
private const val DOMAIN_REFRESH_DELAY_MS = 3_000L

/**
 * 预置域名池并直接放行初始化。
 *
 * 库的请求域名裁决（RetryAndDomainRedirectInterceptor → JmDomainManager.getBestDomain）
 * 只有在域名池处于 READY 之后才返回，否则阻塞到整条初始化链跑完。这里跳过那条链，
 * 让首个请求立刻拿到域名。池为空时不放行 —— 交回库的初始化链更安全。
 */
internal fun primeDomainManager(manager: JmDomainManager, pool: List<String>) {
    if (pool.isEmpty()) return
    manager.updateDomains(pool)
    manager.setInitialized(true)
}

/**
 * 首屏之后刷新线路池：重新拉一次分发服务器下发的官方线路表，重新探活，并把结果交回调用方落盘。
 *
 * 库的 updateDomains 是 protected 且只在初始化链里调用，所以这里自己拉表（[officialApiDomains]）。
 * 拉表失败或探活全军覆没只影响线路新鲜度，当前可用的池保持不动。
 */
internal fun refreshDomainPoolInBackground(
    client: AbstractJmClient,
    custom: List<String>,
    onPoolRefreshed: (List<String>) -> Unit = {},
    fetchOfficial: () -> List<String> = { officialApiDomains() },
    delayMs: Long = DOMAIN_REFRESH_DELAY_MS,
) {
    val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "jm3-domain-refresh").apply { isDaemon = true }
    }
    scheduler.schedule({
        try {
            runCatching {
                val manager = domainManagerOf(client)
                // 探活器取不到时整段刷新跳过：与原先反射失败被 runCatching 吞掉的效果一致，现有池保持不动。
                val pool = domainProbeOf(client)?.let { probe ->
                    refreshDomainPool(
                        manager = manager,
                        custom = custom,
                        fetchOfficial = fetchOfficial,
                        probe = { candidate -> probeDomainPool(candidate, probe) },
                    )
                }
                if (pool != null) onPoolRefreshed(pool)
            }
        } finally {
            scheduler.shutdown()
        }
    }, delayMs, TimeUnit.MILLISECONDS)
}

/**
 * 刷新一次线路池：先探活候选池，再把探得通的域名整体换进管理器。
 *
 * 旧实现先 updateDomains 再探活：替换会清空失败计数，探活期间池里全是 0 计数，
 * 请求侧的 getBestDomain 就会选中已失效的线路。这里把探活挪到替换之前，替换一完成计数就是真实结果。
 *
 * @return 落盘的新池；拉表失败或全部探不通时返回 null，现有池与失败计数保持不动。
 */
internal fun refreshDomainPool(
    manager: JmDomainManager,
    custom: List<String>,
    fetchOfficial: () -> List<String>,
    probe: (List<String>) -> List<String>,
): List<String>? {
    val official = fetchOfficial()
    if (official.isEmpty()) return null
    val candidate = Domains.normalize(custom + official + manager.domains.toList())
    val alive = probe(candidate)
    if (alive.isEmpty()) return null
    manager.updateDomains(alive)
    return alive
}

/**
 * 探活候选池，只留探得通的域名。
 *
 * 探活放在临时管理器上：库的 probeAllDomains 会直接改失败计数，放在真实管理器上就等价于先洗白再探活。
 * 结果由探活回调自己记录 —— 临时管理器没有初始化，读 domainStates 会在 blockUntilInitialized 上永久挂起。
 */
internal fun probeDomainPool(candidate: List<String>, probe: DomainProbe): List<String> {
    if (candidate.isEmpty()) return emptyList()
    val reachable = ConcurrentHashMap.newKeySet<String>()
    val tracking = object : DomainProbe {
        override fun isReachable(domain: String): Boolean =
            probe.isReachable(domain).also { ok -> if (ok) reachable.add(domain) }
    }
    JmDomainManager(candidate).probeAllDomains(tracking)
    return candidate.filter { it in reachable }
}

/**
 * 库初始化链跑完后把线路池交回调用方落盘。
 *
 * 首次安装没有可预置的池，只能先让库自己拉表探活；跑完记住这次的池，下次冷启动就能零等待。
 */
internal fun persistDomainPool(
    client: AbstractJmClient,
    custom: List<String>,
    onPoolRefreshed: (List<String>) -> Unit,
) {
    runCatching {
        val pool = Domains.normalize(custom + domainManagerOf(client).domains.toList())
        if (pool.isNotEmpty()) onPoolRefreshed(pool)
    }
}

private const val TAG = "CustomDomainPool"
