package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.constant.JmConstants
import io.github.jukomu.jmcomic.core.crypto.JmCryptoTool
import io.github.jukomu.jmcomic.core.parser.ApiParser
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 分发服务器线路表的请求超时。只影响后台刷新，用不着挤进首屏的窗口。 */
private const val DOMAIN_TABLE_TIMEOUT_MS = 2_000L

/**
 * 解密分发服务器下发的线路表并取出域名。
 *
 * 表是 AES 密文 JSON，`Server` 字段就是当前可用域名，`jm3_Server` 附带线路名。
 * 解析失败一律视为空表：拿不到新表就保留现有池，不能因为一次坏响应打断刷新。
 */
internal fun domainPoolFromTable(table: String): List<String> = runCatching {
    val json = JmCryptoTool.decryptApiResponse(table.trim(), "", JmConstants.API_DOMAIN_SERVER_SECRET)
    Domains.normalize(ApiParser.parseDomainsFromDomainServer(json))
}.getOrDefault(emptyList())

/**
 * 拉取官方线路表：多个分发服务器并发请求，取最先成功的一张表。
 *
 * 原版（JMComic3 v2.1.12 的 `ke()`）固定请求一个 OSS 地址，库则串行遍历
 * [JmConstants.API_URL_DOMAIN_SERVER_LIST]；这里并发抢答，等待收敛到最快那个节点。
 * 全部失败或超时返回空表，调用方沿用现有池。
 */
internal fun officialApiDomains(
    urls: List<String> = JmConstants.API_URL_DOMAIN_SERVER_LIST,
    fetch: (String) -> String? = { httpGetText(it) },
    timeoutMs: Long = DOMAIN_TABLE_TIMEOUT_MS,
): List<String> {
    if (urls.isEmpty()) return emptyList()
    val pool = Executors.newFixedThreadPool(urls.size) { runnable ->
        Thread(runnable, "jm3-domain-table").apply { isDaemon = true }
    }
    val answer = AtomicReference<List<String>>()
    val first = CountDownLatch(1)
    try {
        urls.forEach { url ->
            pool.execute {
                if (first.count == 0L) return@execute
                val table = runCatching { fetch(url) }.getOrNull()
                if (!table.isNullOrBlank()) {
                    val domains = domainPoolFromTable(table)
                    if (domains.isNotEmpty() && answer.compareAndSet(null, domains)) first.countDown()
                }
            }
        }
        first.await(timeoutMs, TimeUnit.MILLISECONDS)
        return answer.get() ?: emptyList()
    } finally {
        pool.shutdownNow()
    }
}

/** 一跳 GET 的结果：状态码、Location 头与仅 200 才读取的正文。 */
private class DomainTableHop(val code: Int, val location: String?, val body: String?)

/**
 * 发一跳 GET 并断开连接。
 *
 * 显式关闭自动重定向：JDK 的自动跟随会直接打到 Location 指向的任何地址（含明文 HTTP），
 * 本模块只有自己看 3xx 才谈得上逐跳校验。
 */
private fun readHop(url: URI, connectionFactory: (String) -> HttpURLConnection): DomainTableHop {
    val connection = connectionFactory(url.toString()).apply {
        requestMethod = "GET"
        connectTimeout = DOMAIN_TABLE_TIMEOUT_MS.toInt()
        readTimeout = DOMAIN_TABLE_TIMEOUT_MS.toInt()
        instanceFollowRedirects = false
    }
    return try {
        val code = connection.responseCode
        val body = if (code == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        DomainTableHop(code, connection.getHeaderField("Location"), body)
    } finally {
        connection.disconnect()
    }
}

/**
 * 重定向目标只接受 https 且与初始主机同名。
 *
 * 分发服务器是固定 OSS 域名，换路径不会换主机，所以同主机就够了；
 * 跨主机或降级到明文一律判为不可信，宁可用不到新表也不能被 3xx 引去别处。
 */
private fun trustedRedirectTarget(initial: URI, location: String?): URI? {
    if (location.isNullOrBlank()) return null
    val target = runCatching { initial.resolve(location) }.getOrNull() ?: return null
    if (!target.scheme.equals("https", ignoreCase = true)) return null
    val initialHost = initial.host ?: return null
    val targetHost = target.host ?: return null
    return if (targetHost.equals(initialHost, ignoreCase = true)) target else null
}

/**
 * 取线路表的纯文本响应；非 200 或 IO 失败都当这一路不可用。
 *
 * 最多跟一跳受信重定向，第二跳即使同域也放弃：线路表分发不靠多跳跳转。
 *
 * [connectionFactory] 只为单测替换网络出口：重定向策略收在本函数内，用脚本化连接就能覆盖各条跳转分支。
 */
internal fun httpGetText(
    url: String,
    connectionFactory: (String) -> HttpURLConnection = { target ->
        URI(target).toURL().openConnection() as HttpURLConnection
    },
): String? {
    val initial = runCatching { URI(url) }.getOrNull() ?: return null
    val hop = readHop(initial, connectionFactory)
    if (hop.code == HttpURLConnection.HTTP_OK) return hop.body
    if (hop.code !in 300..399) return null
    val target = trustedRedirectTarget(initial, hop.location) ?: return null
    val redirected = readHop(target, connectionFactory)
    return if (redirected.code == HttpURLConnection.HTTP_OK) redirected.body else null
}
