package com.jm3.core

import com.jm3.core.model.CookieSnapshot

/**
 * 清洗域名：去除协议头、端口、路径和前导点，确保符合 OkHttp Cookie/HttpUrl 规范。
 * 例如：".18comic.vip" -> "18comic.vip", "https://18comic.vip/api" -> "18comic.vip"。
 */
internal fun sanitizeDomain(domain: String): String {
    var d = domain.trim()
    if (d.startsWith("https://", ignoreCase = true)) {
        d = d.substring(8)
    } else if (d.startsWith("http://", ignoreCase = true)) {
        d = d.substring(7)
    }
    d = d.substringBefore('/')
    d = d.substringBefore(':')
    d = d.trimStart('.')
    return d.trim()
}

/**
 * 将 [CookieSnapshot] 安全转换为 [okhttp3.Cookie]。
 * 若 domain 格式非法或为空，返回 null 而不是抛出 [IllegalArgumentException]。
 */
internal fun CookieSnapshot.toOkhttpCookie(): okhttp3.Cookie? {
    val clean = sanitizeDomain(domain)
    if (clean.isBlank()) return null
    return runCatching {
        okhttp3.Cookie.Builder()
            .name(name)
            .value(value)
            .domain(clean)
            .path(path.ifBlank { "/" })
            .build()
    }.getOrNull()
}

/**
 * 把会话 Cookie 展开到当前线路域名。
 *
 * 库按 cookie.domain() 把会话注入 CookieJar（[AbstractJmClient.setCookies]），CookieJar 又以请求 host 取 Cookie；
 * 而线路由库在每次启动后重新拉取并按失败计数裁决，登录时的域名未必再被选中，于是已登录却拿不到会话。
 * 把这些 Cookie 复制到每条候选线路后，任一条线路被选中都能带上会话。
 *
 * 候选域名都指向同一站点的镜像，库本来就按需把会话 Cookie 发往其中任意一条，广播不扩大信任边界。
 * 结果先保留清洗后的原 Cookie（顺序不变），再按候选域名逐组追加其余 Cookie 的副本，同一域名下的 Cookie 相邻。
 */
internal fun List<CookieSnapshot>.broadcastTo(domains: Collection<String>): List<CookieSnapshot> {
    val cleanedOriginals = this.mapNotNull {
        val cleanDomain = sanitizeDomain(it.domain)
        if (cleanDomain.isBlank()) null else it.copy(domain = cleanDomain)
    }
    if (cleanedOriginals.isEmpty()) return emptyList()

    val targets = domains.map { sanitizeDomain(it) }.filter { it.isNotBlank() }.distinct()
    if (targets.isEmpty()) return cleanedOriginals

    val expanded = ArrayList<CookieSnapshot>(cleanedOriginals.size * (targets.size + 1))
    expanded += cleanedOriginals
    for (targetDomain in targets) {
        for (cookie in cleanedOriginals) {
            if (cookie.domain != targetDomain) {
                expanded += cookie.copy(domain = targetDomain)
            }
        }
    }
    return expanded.distinctBy { Triple(it.name, it.domain, it.path) }
}

