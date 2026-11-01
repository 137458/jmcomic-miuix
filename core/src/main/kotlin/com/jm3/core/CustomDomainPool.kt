package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.net.provider.JmDomainManager
import java.lang.reflect.Field
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

private fun domainManagerOf(client: AbstractJmClient): JmDomainManager =
    domainManagerField.get(client) as JmDomainManager

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
 * 覆盖域名池会同时清空失败计数，必须重新探活，否则已失效的线路又会被选中。
 * 拉表失败只影响线路新鲜度，当前可用的池保持不动。
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
                val official = fetchOfficial()
                if (official.isEmpty()) return@runCatching
                val manager = domainManagerOf(client)
                val pool = Domains.normalize(custom + official + manager.domains.toList())
                manager.updateDomains(pool)
                client.reprobeDomains()
                onPoolRefreshed(pool)
            }
        } finally {
            scheduler.shutdown()
        }
    }, delayMs, TimeUnit.MILLISECONDS)
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
