package com.jm3.core

import com.jm3.core.settings.Domains
import io.github.jukomu.jmcomic.core.constant.JmConstants
import io.github.jukomu.jmcomic.core.crypto.JmCryptoTool
import io.github.jukomu.jmcomic.core.parser.ApiParser
import java.net.HttpURLConnection
import java.net.URL
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
    fetch: (String) -> String? = ::httpGetText,
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

/** 取线路表的纯文本响应；非 200 或 IO 失败都当这一路不可用。 */
private fun httpGetText(url: String): String? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = DOMAIN_TABLE_TIMEOUT_MS.toInt()
        readTimeout = DOMAIN_TABLE_TIMEOUT_MS.toInt()
        instanceFollowRedirects = true
    }
    return try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            null
        } else {
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    } finally {
        connection.disconnect()
    }
}
