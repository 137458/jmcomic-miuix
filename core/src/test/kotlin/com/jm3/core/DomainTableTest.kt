package com.jm3.core

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `http 跳 https 且同域时由本模块自己跟一跳`() {
        val requested = mutableListOf<String>()
        val text = httpGetText(
            "http://a.example/newsvr.txt",
            scriptedConnections(
                mapOf(
                    "http://a.example/newsvr.txt" to ScriptedResponse(302, location = "https://a.example/newsvr.txt"),
                    "https://a.example/newsvr.txt" to ScriptedResponse(200, body = table),
                ),
                requested,
            ),
        )

        assertEquals(table, text, "同域 https 重定向应被跟随并读到线路表")
        assertEquals(
            listOf("http://a.example/newsvr.txt", "https://a.example/newsvr.txt"),
            requested,
            "重定向必须由本模块自己发出 https 一跳",
        )
    }

    @Test
    fun `跳到 http 或跨域或缺 Location 时拒绝并返回 null`() {
        val script = mapOf(
            "https://a.example/plain.txt" to ScriptedResponse(302, location = "http://a.example/plain.txt"),
            "http://a.example/plain.txt" to ScriptedResponse(200, body = table),
            "https://a.example/other.txt" to ScriptedResponse(302, location = "https://b.example/newsvr.txt"),
            "https://b.example/newsvr.txt" to ScriptedResponse(200, body = table),
            "https://a.example/nolocation.txt" to ScriptedResponse(302),
        )
        val requested = mutableListOf<String>()

        assertNull(
            httpGetText("https://a.example/plain.txt", scriptedConnections(script, requested)),
            "3xx 指向明文 HTTP 时不得跟随",
        )
        assertNull(
            httpGetText("https://a.example/other.txt", scriptedConnections(script, requested)),
            "3xx 跨域时不得跟随",
        )
        assertNull(
            httpGetText("https://a.example/nolocation.txt", scriptedConnections(script, requested)),
            "3xx 缺 Location 时不得跟随",
        )
        assertEquals(
            listOf("https://a.example/plain.txt", "https://a.example/other.txt", "https://a.example/nolocation.txt"),
            requested,
            "被拒的重定向不得发起第二跳请求",
        )
    }

    @Test
    fun `同域 https 连跳两跳时只跟第一跳`() {
        val requested = mutableListOf<String>()
        val text = httpGetText(
            "https://a.example/one.txt",
            scriptedConnections(
                mapOf(
                    "https://a.example/one.txt" to ScriptedResponse(302, location = "https://a.example/two.txt"),
                    "https://a.example/two.txt" to ScriptedResponse(302, location = "https://a.example/three.txt"),
                    "https://a.example/three.txt" to ScriptedResponse(200, body = table),
                ),
                requested,
            ),
        )

        assertNull(text, "只允许跟一跳受信重定向，第二跳必须放弃")
        assertEquals(
            listOf("https://a.example/one.txt", "https://a.example/two.txt"),
            requested,
            "不得发出第三跳请求",
        )
    }

    @Test
    fun `非 200 且非 3xx 的响应直接放弃`() {
        val requested = mutableListOf<String>()
        val text = httpGetText(
            "https://a.example/broken.txt",
            scriptedConnections(mapOf("https://a.example/broken.txt" to ScriptedResponse(500)), requested),
        )

        assertNull(text, "服务端错误不得当成线路表")
        assertEquals(listOf("https://a.example/broken.txt"), requested, "非 3xx 不得跟随")
    }
}

/** 脚本化的一跳应答：状态码、Location 头与正文。 */
private class ScriptedResponse(
    val code: Int,
    val location: String? = null,
    val body: String? = null,
)

/** 按 URL 出脚本化应答，并记录被测代码真正请求过的地址。 */
private fun scriptedConnections(
    script: Map<String, ScriptedResponse>,
    requested: MutableList<String>,
): (String) -> HttpURLConnection = { url ->
    requested += url
    ScriptedConnection(url, script)
}

/**
 * 复刻 [HttpURLConnection] 的真实语义：`instanceFollowRedirects` 打开时连接自己把 3xx 链走到底，
 * 调用方只看到最终 200；关闭时 3xx 与 Location 原样交回调用方。
 *
 * 于是「谁在跟随重定向」变成可断言的差别：本模块必须关掉自动跟随、自己逐跳校验。
 */
private class ScriptedConnection(
    url: String,
    private val script: Map<String, ScriptedResponse>,
) : HttpURLConnection(URI(url).toURL()) {

    private val requestedUrl: String = url

    private val effective: ScriptedResponse by lazy {
        resolveEffective(requestedUrl, instanceFollowRedirects, script)
    }

    override fun connect() {}

    override fun disconnect() {}

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = effective.code

    override fun getHeaderField(name: String?): String? =
        if (name.equals("Location", ignoreCase = true)) effective.location else null

    override fun getInputStream(): InputStream = (effective.body ?: "").byteInputStream()
}

/** 自动跟随只在 [followRedirects] 为真时发生，与 JDK 行为一致；脚本里缺失的地址按 404 处理。 */
private fun resolveEffective(
    start: String,
    followRedirects: Boolean,
    script: Map<String, ScriptedResponse>,
): ScriptedResponse {
    var current = start
    repeat(MAX_AUTO_HOPS) {
        val response = script[current] ?: return ScriptedResponse(HttpURLConnection.HTTP_NOT_FOUND)
        val location = response.location
        if (!followRedirects || response.code !in 300..399 || location == null) return response
        current = URI(current).resolve(location).toString()
    }
    return ScriptedResponse(HttpURLConnection.HTTP_NOT_FOUND)
}

private const val MAX_AUTO_HOPS = 10
