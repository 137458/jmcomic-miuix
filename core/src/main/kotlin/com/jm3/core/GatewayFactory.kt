package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration
import io.github.jukomu.jmcomic.core.net.OkHttpBuilder

/**
 * 按线路表构建网关。
 *
 * 启动期的域名只有两处来源：设置页填的自定义线路，以及上次刷新落盘的线路池。
 * 官方线路表不写进代码 —— 它由分发服务器下发（见 DomainTable.kt），随服务端变更而变；
 * 拉不到就沿用上次的池，池也没有时才回退到库自己的初始化链。
 */
object GatewayFactory {

    /**
     * 单域名探活超时。
     *
     * 库默认值偏大，而初始化链的探活要等全部域名返回（不是首个成功就放行），
     * 线路表里只要有一个死域名，冷启动就被钉住一个完整超时。
     */
    private const val DOMAIN_PROBE_TIMEOUT_MS = 1_500L

    /** 请求重试次数：库默认 5 次会让失效线路的重试链把首屏拖长。 */
    private const val RETRY_TIMES = 3

    /**
     * @param apiDomains 设置页的自定义线路，优先级最高。
     * @param cachedDomains 上次刷新落盘的线路池，供冷启动直接预置。
     * @param onPoolRefreshed 刷新出新的线路池后回调，由调用方负责持久化（:core 不碰存储）。
     */
    fun create(
        apiDomains: List<String>,
        cachedDomains: List<String> = emptyList(),
        onPoolRefreshed: (List<String>) -> Unit = {},
    ): Jm3Gateway {
        val custom = Domains.normalize(apiDomains)
        val pool = startupDomainPool(custom, Domains.normalize(cachedDomains))
        return Jm3GatewayImpl.production {
            val builder = JmConfiguration.Builder()
                .domainProbeTimeoutMs(DOMAIN_PROBE_TIMEOUT_MS)
                .retryTimes(RETRY_TIMES)
            if (custom.isNotEmpty()) builder.apiDomains(custom)
            val config = builder.build()
            val context = OkHttpBuilder.build(config)
            if (pool.isEmpty()) {
                // 首次安装且没填自定义线路：没有可预置的池，只能走库的初始化链
                // （拉分发服务器线路表 → 全域名探活 → setting，一次性约 3.5s），
                // 跑完记住这次的池，下次冷启动就能零等待。
                val client = JmApiClient(config, context.client, context.cookieManager, context.domainManager)
                client.initializeAsync().whenComplete { _, _ ->
                    persistDomainPool(client, custom, onPoolRefreshed)
                }
                client
            } else {
                // 库的 newApiClient 会立刻异步跑整条初始化，而请求侧的域名裁决
                // （RetryAndDomainRedirectInterceptor → getBestDomain）要 blockUntilInitialized 等它跑完，
                // 首屏因此固定多等 3.5-4.2s。这里自己装配客户端：先预置已知可用池并放行，
                // 线路表的刷新推到首屏之后。
                primeDomainManager(context.domainManager, pool)
                val client = JmApiClient(config, context.client, context.cookieManager, context.domainManager)
                refreshDomainPoolInBackground(client, custom, onPoolRefreshed)
                client
            }
        }
    }
}
