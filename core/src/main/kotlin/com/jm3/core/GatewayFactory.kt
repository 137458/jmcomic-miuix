package com.jm3.core

import io.github.jukomu.jmcomic.core.JmComic
import io.github.jukomu.jmcomic.core.config.JmConfiguration

/** 按自定义 API 线路构建网关；空表 = 库默认域名解析。 */
object GatewayFactory {

    fun create(apiDomains: List<String>): Jm3Gateway = Jm3GatewayImpl {
        @Suppress("DEPRECATION")
        val builder = JmConfiguration.Builder()
        if (apiDomains.isNotEmpty()) builder.apiDomains(apiDomains)
        JmComic.newApiClient(builder.build())
    }
}
