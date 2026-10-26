package com.jm3.core.settings

/** 线路域名归一化：去空白项、去协议头、去尾部斜杠、同项去重保序。 */
object Domains {

    fun normalize(raw: List<String>): List<String> =
        raw.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.removePrefix("https://").removePrefix("http://").trimEnd('/') }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
}
