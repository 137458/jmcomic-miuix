package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertTrue

class GatewayFactoryTest {

    @Test
    fun `GatewayFactory 预置自定义与缓存线路时 domainsReady 为 true 且 close 安全释放`() {
        val gateway = GatewayFactory.create(
            apiDomains = listOf(" https://custom.example/ "),
            cachedDomains = listOf("cached.example", "custom.example"),
        )
        try {
            assertTrue(gateway.domainsReady())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `GatewayFactory 仅传缓存线路时同样直接就绪`() {
        val gateway = GatewayFactory.create(
            apiDomains = emptyList(),
            cachedDomains = listOf("cached.example"),
        )
        try {
            assertTrue(gateway.domainsReady())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `GatewayFactory 在无自定义且无缓存线路时可关闭不崩溃`() {
        val gateway = GatewayFactory.create(
            apiDomains = listOf("  "),
            cachedDomains = emptyList(),
        )
        // 未触发真实网络前直接关闭不应抛出异常
        gateway.close()
    }
}
