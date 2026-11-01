package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.net.provider.JmDomainManager
import java.lang.reflect.Field
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 把用户自定义线路并入库内域名池。
 *
 * 库在初始化时用分发服务器下发的线路表整体替换域名池
 * （io.github.jukomu.jmcomic.core.net.provider.JmDomainManager.updateDomains），
 * 因此自定义线路必须在初始化完成后补入，否则设置页填的线路会被静默覆盖。
 *
 * @return 是否真的补入了新线路；true 表示调用方需要重新探活。
 */
internal fun mergeCustomDomains(pool: MutableList<String>, custom: List<String>): Boolean {
    val missing = custom.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it !in pool }
        .distinct()
        .toList()
    if (missing.isEmpty()) return false
    pool.addAll(0, missing)
    return true
}

/**
 * 启动域名池：自定义线路优先，其次上次探活留下的池，最后内置兜底；跨组去重保序。
 *
 * 顺序即优先级：库的 getBestDomain 取失败计数最小的域名，并列时取池中靠前者。
 */
internal fun startupDomainPool(
    custom: List<String>,
    cached: List<String>,
    fallback: List<String>,
): List<String> = Domains.normalize(custom + cached + fallback)

/** 库没有公开替换域名池的入口，只能读私有字段；字段名变更时惰性失败而非拖垮类初始化。 */
private val domainManagerField: Field by lazy {
    AbstractJmClient::class.java.getDeclaredField("domainManager").apply { isAccessible = true }
}

private fun domainManagerOf(client: AbstractJmClient): JmDomainManager =
    domainManagerField.get(client) as JmDomainManager

/** 首屏之后再补跑库初始化的延迟：留出首屏请求的窗口，避免探活抢首屏带宽。 */
private const val DOMAIN_REFRESH_DELAY_MS = 3_000L

/**
 * 预置域名池并直接放行初始化。
 *
 * 库的请求域名裁决（RetryAndDomainRedirectInterceptor → JmDomainManager.getBestDomain）
 * 只有在域名池处于 READY 之后才返回，否则阻塞到整条初始化链跑完。这里跳过那条链，
 * 让首个请求立刻拿到域名。池为空时也不阻塞，由库报「无可用域名」而不是把调用方挂死。
 */
internal fun primeDomainManager(manager: JmDomainManager, pool: List<String>) {
    if (pool.isNotEmpty()) manager.updateDomains(pool)
    manager.setInitialized(true)
}

/**
 * 首屏之后补跑库的初始化：拉取分发服务器下发的官方线路表并重新探活。
 *
 * 库的 updateDomains 是 protected，只能借整条初始化链去拉官方线路表；它跑的时机被推迟到首屏之后。
 * 覆盖域名池会同时清空失败计数，所以必须重新探活，否则已失效的线路又会被选中。
 * 刷新失败只影响线路新鲜度，当前可用的池保持不动。
 */
internal fun refreshDomainPoolInBackground(client: AbstractJmClient, custom: List<String>) {
    val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "jm3-domain-refresh").apply { isDaemon = true }
    }
    scheduler.schedule({
        try {
            runCatching {
                val manager = domainManagerOf(client)
                val previous = manager.domains.toList()
                client.initializeAsync().join()
                manager.updateDomains(Domains.normalize(custom + manager.domains.toList() + previous))
                client.reprobeDomains()
            }
        } finally {
            scheduler.shutdown()
        }
    }, DOMAIN_REFRESH_DELAY_MS, TimeUnit.MILLISECONDS)
}
