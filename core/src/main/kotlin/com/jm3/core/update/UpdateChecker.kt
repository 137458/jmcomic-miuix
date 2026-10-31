package com.jm3.core.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 传输层 HTTP 失败：携带状态码，由 [UpdateChecker] 映射成 [UpdateFailure]。 */
class UpdateHttpException(val code: Int) : Exception("HTTP $code")

/**
 * GitHub Releases 检查更新。
 *
 * 取舍一：这个网络入口不挂在 Jm3Gateway 上。Jm3Gateway 是 JM 协议（禁漫 JSON API）的唯一出口，
 * 承载域名切换、重试与 Cookie；GitHub 是外部公共探针，协议不同、域名不同，也不需要 JM 的会话与鉴权，
 * 塞进网关会把两种失败模型混在一起。因此这里用独立的 HttpURLConnection 直连，
 * 并以 [LATEST_RELEASE_URL] 常量固定地址。
 *
 * 取舍二：返回 [UpdateCheckResult] 而不是抛异常。失败原因（断网/限流/无 Release）本身就是界面要展示的
 * 内容，用返回值承载可以让调用方无法遗漏失败分支；内部仍在 [Dispatchers.IO] 上做阻塞网络读取。
 *
 * [fetchText] 可注入，便于测试在不联网的情况下覆盖所有失败分支。
 */
class UpdateChecker(
    private val fetchText: (String) -> String = ::fetchReleaseText,
) {

    suspend fun check(localVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val text = try {
            fetchText(LATEST_RELEASE_URL)
        } catch (error: UpdateHttpException) {
            return@withContext UpdateCheckResult.Failed(failureForStatusCode(error.code), "HTTP ${error.code}")
        } catch (error: IOException) {
            // 含 SocketTimeoutException / UnknownHostException：连接、读取失败都归网络问题
            return@withContext UpdateCheckResult.Failed(
                UpdateFailure.NETWORK,
                error.message ?: error.javaClass.simpleName,
            )
        } catch (error: Exception) {
            return@withContext UpdateCheckResult.Failed(
                UpdateFailure.NETWORK,
                error.message ?: error.javaClass.simpleName,
            )
        }

        if (text.isBlank()) {
            return@withContext UpdateCheckResult.Failed(UpdateFailure.RESPONSE, "响应体为空")
        }

        val release = try {
            ReleaseJson.parse(text)
        } catch (error: UpdateParseException.Malformed) {
            return@withContext UpdateCheckResult.Failed(
                UpdateFailure.RESPONSE,
                error.message ?: "响应不是合法 JSON",
            )
        } catch (error: UpdateParseException.MissingField) {
            return@withContext UpdateCheckResult.Failed(UpdateFailure.MISSING_FIELD, error.field)
        }

        UpdateCheckResult.Success(
            release = release,
            relation = VersionCompare.relation(localVersion, release.tagName),
        )
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/137458/jmcomic-miuix/releases/latest"

        private fun failureForStatusCode(code: Int): UpdateFailure = when (code) {
            404 -> UpdateFailure.NOT_FOUND
            403, 429 -> UpdateFailure.RATE_LIMITED
            else -> UpdateFailure.HTTP
        }
    }
}

/**
 * 用 JDK 自带的 HttpURLConnection 读取响应文本，非 2xx 抛 [UpdateHttpException]。
 *
 * GitHub API 要求非空 User-Agent，缺失会直接 403，所以必须显式设置。
 */
private fun fetchReleaseText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.errorStream?.close()
            throw UpdateHttpException(code)
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 10_000
private const val USER_AGENT = "jm3-android"
