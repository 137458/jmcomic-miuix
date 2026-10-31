package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReleaseJsonTest {

    @Test
    fun 解析正常响应() {
        val release = ReleaseJson.parse(
            """
            {
              "tag_name": "v1.0.1",
              "name": "JM3 v1.0.1",
              "body": "## 新增\n- 检查更新\n",
              "html_url": "https://github.com/137458/jmcomic-miuix/releases/tag/v1.0.1",
              "published_at": "2026-10-31T06:12:34Z",
              "assets": [{"name": "app-release.apk", "browser_download_url": "https://example.com/a.apk"}]
            }
            """.trimIndent(),
        )
        assertEquals("v1.0.1", release.tagName)
        assertEquals("1.0.1", release.version)
        assertEquals("JM3 v1.0.1", release.name)
        assertEquals("## 新增\n- 检查更新\n", release.notes)
        assertEquals("https://github.com/137458/jmcomic-miuix/releases/tag/v1.0.1", release.htmlUrl)
        assertEquals("2026-10-31T06:12:34Z", release.publishedAt)
        assertEquals("2026-10-31", release.publishedDate)
    }

    @Test
    fun 更新说明保留原文换行与Markdown标记() {
        val release = ReleaseJson.parse(
            """
            {
              "tag_name": "v1.1.0",
              "html_url": "https://example.com/r",
              "body": "## 新增\n\n- 检查更新\n- 更新页 UI\n\n**注意**：需重新登录\n"
            }
            """.trimIndent(),
        )
        assertEquals("## 新增\n\n- 检查更新\n- 更新页 UI\n\n**注意**：需重新登录\n", release.notes)
    }

    @Test
    fun 缺少tag_name抛缺字段异常() {
        val error = assertFailsWith<UpdateParseException.MissingField> {
            ReleaseJson.parse("""{"html_url":"https://example.com/r","published_at":"2026-10-31T06:12:34Z"}""")
        }
        assertEquals("tag_name", error.field)
        assertEquals("响应缺少字段：tag_name", error.message)
    }

    @Test
    fun 缺少html_url抛缺字段异常() {
        val error = assertFailsWith<UpdateParseException.MissingField> {
            ReleaseJson.parse("""{"tag_name":"v1.0.0"}""")
        }
        assertEquals("html_url", error.field)
    }

    @Test
    fun 可选字段缺失时取空串() {
        val release = ReleaseJson.parse("""{"tag_name":"v1.0.0","html_url":"https://example.com/r"}""")
        assertEquals("", release.name)
        assertEquals("", release.notes)
        assertEquals("", release.publishedAt)
        assertEquals("", release.publishedDate)
    }

    @Test
    fun 空字符串字段按缺失处理() {
        val release = ReleaseJson.parse(
            """{"tag_name":"v1.0.0","name":"","body":"","html_url":"https://example.com/r","published_at":""}""",
        )
        assertEquals("", release.name)
        assertEquals("", release.notes)
        assertFailsWith<UpdateParseException.MissingField> {
            ReleaseJson.parse("""{"tag_name":"","html_url":"https://example.com/r"}""")
        }
    }

    @Test
    fun 非法JSON抛格式异常() {
        assertFailsWith<UpdateParseException.Malformed> { ReleaseJson.parse("not json") }
        assertFailsWith<UpdateParseException.Malformed> { ReleaseJson.parse("") }
        assertFailsWith<UpdateParseException.Malformed> { ReleaseJson.parse("""[{"tag_name":"v1.0.0"}]""") }
        assertFailsWith<UpdateParseException.Malformed> { ReleaseJson.parse("""{"tag_name":"v1.0.0",""") }
    }

    @Test
    fun 解析assets数组并给出可用安装包() {
        val release = ReleaseJson.parse(
            """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://example.com/r",
              "assets": [
                {"name": "mapping.txt", "size": 120, "browser_download_url": "https://example.com/mapping.txt"},
                {"name": "jm3-v1.2.0-universal.apk", "size": 20971520, "browser_download_url": "https://example.com/a.apk"},
                {"name": "symbols.zip", "size": 4096, "browser_download_url": "https://example.com/symbols.zip"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ReleaseAsset("mapping.txt", 120, "https://example.com/mapping.txt"),
                ReleaseAsset("jm3-v1.2.0-universal.apk", 20971520, "https://example.com/a.apk"),
                ReleaseAsset("symbols.zip", 4096, "https://example.com/symbols.zip"),
            ),
            release.assets,
        )
        assertEquals("jm3-v1.2.0-universal.apk", release.apk?.name)
        assertEquals(20971520L, release.apk?.size)
    }

    @Test
    fun 缺少assets或类型不符时为空列表() {
        val missing = ReleaseJson.parse("""{"tag_name":"v1.0.0","html_url":"https://example.com/r"}""")
        assertEquals(emptyList<ReleaseAsset>(), missing.assets)
        assertNull(missing.apk)

        for (raw in listOf("null", "\"oops\"", "123", "{}")) {
            val release = ReleaseJson.parse(
                """{"tag_name":"v1.0.0","html_url":"https://example.com/r","assets":$raw}""",
            )
            assertEquals(emptyList<ReleaseAsset>(), release.assets, raw)
        }
    }

    @Test
    fun assets元素缺字段或地址无效时跳过() {
        val release = ReleaseJson.parse(
            """
            {
              "tag_name": "v1.0.0",
              "html_url": "https://example.com/r",
              "assets": [
                {"size": 1, "browser_download_url": "https://example.com/no-name.apk"},
                {"name": "no-url.apk", "size": 2},
                {"name": "", "size": 3, "browser_download_url": "https://example.com/empty-name.apk"},
                {"name": "empty-url.apk", "size": 4, "browser_download_url": ""},
                {"name": "blank-url.apk", "size": 5, "browser_download_url": "   "},
                {"name": "no-object-field"},
                "just-a-string",
                {"name": "ok.apk", "size": 6, "browser_download_url": "https://example.com/ok.apk"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf(ReleaseAsset("ok.apk", 6, "https://example.com/ok.apk")), release.assets)
        assertEquals("ok.apk", release.apk?.name)
    }

    @Test
    fun assets元素size缺失或非数字时为0() {
        val release = ReleaseJson.parse(
            """
            {
              "tag_name": "v1.0.0",
              "html_url": "https://example.com/r",
              "assets": [
                {"name": "missing.apk", "browser_download_url": "https://example.com/a.apk"},
                {"name": "text.apk", "size": "big", "browser_download_url": "https://example.com/b.apk"},
                {"name": "null.apk", "size": null, "browser_download_url": "https://example.com/c.apk"},
                {"name": "float.apk", "size": 1.5, "browser_download_url": "https://example.com/d.apk"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(4, release.assets.size)
        assertEquals(listOf(0L, 0L, 0L, 0L), release.assets.map { it.size })
    }
}
