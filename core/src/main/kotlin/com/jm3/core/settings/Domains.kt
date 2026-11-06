package com.jm3.core.settings

import com.jm3.core.CoverLocator

/** 线路域名归一化：去空白项、去协议头、去尾部斜杠、同项去重保序；取值口径与 [CoverLocator.hostOf] 同一份。 */
object Domains {

    /**
     * 兜底官方镜像：库内线路裁决在冷启动时还没就绪，首批请求也要能带上会话。
     *
     * 只此一处定义：网关铺 Cookie 与库内登录域兜底都从这里取，改一处就够。
     */
    val FALLBACK_MIRRORS: List<String> = listOf("18comic.vip", "18comic.org")

    fun normalize(raw: List<String>): List<String> =
        raw.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { CoverLocator.hostOf(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /** 落盘文本解码：一行一条，逐行归一（旧数据里的协议头、空行、重复项在这一步收敛）。 */
    fun decode(raw: String?): List<String> = normalize(raw?.split('\n') ?: emptyList())

    /** 落盘文本编码：先归一再拼接，保证 `decode(encode(x))` 与 `normalize(x)` 一致。 */
    fun encode(domains: List<String>): String = normalize(domains).joinToString("\n")
}
