package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainTableTest {

    /** 分发服务器下发的真实线路表密文（428 字符），解密应还原成当前线路。 */
    private val table =
        "X+bnzYIcwF6C7Rd3T7njPDNH08zsH9zyqCrrjCr7qcmB/zK/eAnvSu++oR6PIlaEHE6OuhvrxEzwciVvhdU0I9OYcmWTxF1K7fLfcwkn7kMQg2DZ2qpE7dKGkqKCmQijaSUOswxL1/p9pSVe/vRYE94meOg46EoE6PPTzlAnY8RK629QiqQndRXM9bijtZuYJtKw3YBAA26a+fy06dNszfw9v/4R8akVaSTWLOJc0nJy+9vm2t2W997vcqFL91iklKuKVEZHTtdpaLTgWExXaAo5cYCEG4BfCg8DTzruNxOLL7o02c6WB4HV69s1VqCJYl+3l3RNwDjJ0iRNnG9p/caZL/y6sT8i78Wc38WZhOAxkDsOFiGNpvS3eojKA0wGhpTtWsuUrXgSZ7ilEPttJvOhrJGGRJJ7Ux4HgzDYO1A="

    @Test
    fun `真实密文解密出官方线路表`() {
        assertEquals(
            listOf("www.cdnhjk.net", "www.cdnutc.cc", "www.cdngwc.net", "www.cdngwc.club"),
            domainPoolFromTable(table),
        )
    }

    @Test
    fun `无法解密的表视为空`() {
        assertEquals(emptyList(), domainPoolFromTable("not-a-table"))
    }

    @Test
    fun `首个分发服务器失败时改用下一个的表`() {
        val domains = officialApiDomains(
            urls = listOf("https://a.example/newsvr.txt", "https://b.example/newsvr.txt"),
            fetch = { url -> if (url.contains("b.example")) table else null },
        )
        assertEquals(listOf("www.cdnhjk.net", "www.cdnutc.cc", "www.cdngwc.net", "www.cdngwc.club"), domains)
    }

    @Test
    fun `有服务器返回垃圾内容时继续等其他服务器的有效表`() {
        val domains = officialApiDomains(
            urls = listOf("https://a.example/newsvr.txt", "https://b.example/newsvr.txt"),
            fetch = { url -> if (url.contains("b.example")) table else "garbage" },
        )
        assertTrue(domains.isNotEmpty())
    }

    @Test
    fun `全部分发服务器失败返回空表`() {
        val domains = officialApiDomains(
            urls = listOf("https://a.example/newsvr.txt", "https://b.example/newsvr.txt"),
            fetch = { null },
            timeoutMs = 200,
        )
        assertEquals(emptyList(), domains)
    }
}
