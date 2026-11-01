package com.jm3.app.miuix.data

import com.jm3.core.settings.DomainPoolCache
import com.jm3.core.settings.Domains
import java.io.File

/**
 * 线路池缓存的落盘实现：`filesDir/api_domain_pool.txt`，一行一条裸域名。
 *
 * 走不了 DataStore —— 网关装配发生在首屏请求之前且是同步的，等不到挂起读取。
 * 内容跟设置项无关：它只是「上次刷新出来的可用线路」的快照，丢了会重新拉一次线路表。
 */
class FileDomainPoolCache(private val file: File) : DomainPoolCache {

    override fun read(): List<String> = runCatching {
        if (file.isFile) Domains.normalize(file.readLines()) else emptyList()
    }.getOrDefault(emptyList())

    override fun write(pool: List<String>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(Domains.normalize(pool).joinToString("\n"))
        }
    }
}
