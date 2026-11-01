package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration
import io.github.jukomu.jmcomic.core.net.OkHttpBuilder

/** 按自定义 API 线路构建网关；空表 = 库官方线路表。 */
object GatewayFactory {

    /**
     * 单域名探活超时。
     *
     * 库默认值偏大，而启动期的探活要等全部域名返回（不是首个成功就放行），
     * 默认线路表里只要有一个死域名，冷启动就被钉住一个完整超时。
     */
    private const val DOMAIN_PROBE_TIMEOUT_MS = 1_500L

    /** 请求重试次数：库默认 5 次会让失效线路的重试链把首屏拖长。 */
    private const val RETRY_TIMES = 3

    /**
     * 内置兜底线路。
     *
     * 库自带的 JmConstants.DEFAULT_API_DOMAINS 长期未更新（当前实测全部 403 或连接失败），
     * 不能用它兜底；这里只放实测可用的域名，且顺序即首次请求的优先顺序
     * （库的 getBestDomain 取失败计数最小的域名，并列时取池中靠前者）。
     */
    private val FALLBACK_DOMAINS = listOf(
        "www.cdngwc.net",
        "www.cdngwc.club",
        "www.cdnhjk.net",
        "www.cdnutc.cc",
    )

    fun create(apiDomains: List<String>): Jm3Gateway {
        val custom = Domains.normalize(apiDomains)
        val pool = startupDomainPool(custom, emptyList(), FALLBACK_DOMAINS)
        return Jm3GatewayImpl.production {
            val builder = JmConfiguration.Builder()
                .domainProbeTimeoutMs(DOMAIN_PROBE_TIMEOUT_MS)
                .retryTimes(RETRY_TIMES)
            if (custom.isNotEmpty()) builder.apiDomains(custom)
            val config = builder.build()
            // 库的 newApiClient 会立刻异步跑「分发服务器拉线路表 → 全域名探活 → setting」，
            // 而请求侧的域名裁决（RetryAndDomainRedirectInterceptor → getBestDomain）
            // 要 blockUntilInitialized 等它全部跑完，首屏因此固定多等 3.5-4.2s。
            // 这里改为自己装配客户端：先用已知可用池预置并放行，把那三段挪到首屏之后再补。
            val context = OkHttpBuilder.build(config)
            primeDomainManager(context.domainManager, pool)
            val client = JmApiClient(config, context.client, context.cookieManager, context.domainManager)
            refreshDomainPoolInBackground(client, custom)
            client
        }
    }
}
