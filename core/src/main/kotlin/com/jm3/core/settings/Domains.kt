package com.jm3.core.settings

import com.jm3.core.CoverLocator

/** 线路域名归一化：去空白项、去协议头、去尾部斜杠、同项去重保序；取值口径与 [CoverLocator.hostOf] 同一份。 */
object Domains {

    fun normalize(raw: List<String>): List<String> =
        raw.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { CoverLocator.hostOf(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
}
