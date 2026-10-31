package com.jm3.core

import com.jm3.core.model.CookieSnapshot

/**
 * 把会话 Cookie 展开到当前线路域名。
 *
 * 库按 cookie.domain() 把会话注入 CookieJar（[AbstractJmClient.setCookies]），CookieJar 又以请求 host 取 Cookie；
 * 而线路由库在每次启动后重新拉取并按失败计数裁决，登录时的域名未必再被选中，于是已登录却拿不到会话。
 * 把这些 Cookie 复制到每条候选线路后，任一条线路被选中都能带上会话。
 *
 * 候选域名都指向同一站点的镜像，库本来就按需把会话 Cookie 发往其中任意一条，广播不扩大信任边界。
 * 结果先保留原 Cookie（顺序不变），再按候选域名逐组追加其余 Cookie 的副本，同一域名下的 Cookie 相邻。
 */
internal fun List<CookieSnapshot>.broadcastTo(domains: Collection<String>): List<CookieSnapshot> {
    val targets = domains.filter { it.isNotBlank() }.distinct()
    if (targets.isEmpty()) return this
    val expanded = ArrayList<CookieSnapshot>(size * (targets.size + 1))
    expanded += this
    for (domain in targets) {
        for (cookie in this) {
            if (cookie.domain != domain) expanded += cookie.copy(domain = domain)
        }
    }
    return expanded.distinctBy { Triple(it.name, it.domain, it.path) }
}
