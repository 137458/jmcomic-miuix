package com.jm3.core.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateCheckerTest {

    /** 只提供检查更新关心的字段，其余留空以固定解析面。 */
    private fun releaseJson(tag: String) =
        """{"tag_name":"$tag","name":"$tag","body":"## 变更","html_url":"https://example.com/r","published_at":"2026-10-31T06:12:34Z"}"""

    @Test
    fun 远端版本更高时给出有更新() = runTest {
        val checker = UpdateChecker { releaseJson("v1.1.0") }
        val result = assertIs<UpdateCheckResult.Success>(checker.check("1.0.0"))
        assertEquals(VersionRelation.NEWER, result.relation)
        assertEquals("1.1.0", result.release.version)
        assertEquals("## 变更", result.release.notes)
    }

    @Test
    fun 两端同版本时给出相同() = runTest {
        val checker = UpdateChecker { releaseJson("v1.0.0") }
        val result = assertIs<UpdateCheckResult.Success>(checker.check("1.0.0"))
        assertEquals(VersionRelation.SAME, result.relation)
    }

    @Test
    fun 本地版本更高时给出本地领先() = runTest {
        val checker = UpdateChecker { releaseJson("v1.0.0") }
        val result = assertIs<UpdateCheckResult.Success>(checker.check("1.1.0"))
        assertEquals(VersionRelation.OLDER, result.relation)
    }

    @Test
    fun 请求使用固定的latest地址() = runTest {
        var requested: String? = null
        val checker = UpdateChecker { url ->
            requested = url
            releaseJson("v1.0.0")
        }
        checker.check("1.0.0")
        assertEquals(UpdateChecker.LATEST_RELEASE_URL, requested)
    }

    @Test
    fun 仓库无Release时映射为未找到() = runTest {
        val checker = UpdateChecker { throw UpdateHttpException(404) }
        val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
        assertEquals(UpdateFailure.NOT_FOUND, result.failure)
        assertTrue(result.message.contains("暂无 Release"), result.message)
    }

    @Test
    fun 限流状态码映射为限流() = runTest {
        for (code in listOf(403, 429)) {
            val checker = UpdateChecker { throw UpdateHttpException(code) }
            val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
            assertEquals(UpdateFailure.RATE_LIMITED, result.failure, "HTTP \$code")
            assertTrue(result.detail.contains(code.toString()), result.detail)
        }
    }

    @Test
    fun 其他非成功状态码映射为HTTP错误() = runTest {
        val checker = UpdateChecker { throw UpdateHttpException(500) }
        val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
        assertEquals(UpdateFailure.HTTP, result.failure)
        assertTrue(result.detail.contains("500"), result.detail)
    }

    @Test
    fun 传输异常映射为网络错误() = runTest {
        val checker = UpdateChecker { throw IOException("boom") }
        val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
        assertEquals(UpdateFailure.NETWORK, result.failure)
        assertTrue(result.detail.contains("boom"), result.detail)
    }

    @Test
    fun 响应为空映射为响应异常() = runTest {
        for (body in listOf("", "   ")) {
            val checker = UpdateChecker { body }
            val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
            assertEquals(UpdateFailure.RESPONSE, result.failure)
        }
    }

    @Test
    fun 响应不是JSON映射为响应异常() = runTest {
        val checker = UpdateChecker { "<html>502 Bad Gateway</html>" }
        val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
        assertEquals(UpdateFailure.RESPONSE, result.failure)
    }

    @Test
    fun 响应缺少必需字段映射为缺字段() = runTest {
        val checker = UpdateChecker { """{"html_url":"https://example.com/r"}""" }
        val result = assertIs<UpdateCheckResult.Failed>(checker.check("1.0.0"))
        assertEquals(UpdateFailure.MISSING_FIELD, result.failure)
        assertTrue(result.detail.contains("tag_name"), result.detail)
    }
}
