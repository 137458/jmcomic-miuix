package com.jm3.app.miuix.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.jm3.app.miuix.update.UpdateDownloadState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 更新弹窗主按钮的文案契约。
 *
 * 弹窗的显隐由页面管，这里只钉住一件用户能看见的事：主按钮这一格里到底写什么。两种维度参与
 * 决策——有没有安装包、下载到了哪一步；下载中要把百分比写进按钮，其它状态都要回到"立即下载"，
 * 否则下载失败后按钮会停在"下载中"，用户点不动也不知道该重试。百分比与页面下载卡共用
 * [updateProgressText]，两处只差前缀与是否带体积。
 */
class UpdateDialogTest {

    private fun downloading(downloaded: Long, total: Long) =
        UpdateDownloadState.Downloading(downloaded = downloaded, total = total, speedBytesPerSec = 1_000_000L)

    @Test
    fun `idle state with apk offers download`() {
        assertEquals(
            "立即下载",
            updateDialogPrimaryText(hasApk = true, downloadState = UpdateDownloadState.Idle),
        )
    }

    @Test
    fun `idle state without apk falls back to releases page`() {
        assertEquals(
            "前往 Releases 页面",
            updateDialogPrimaryText(hasApk = false, downloadState = UpdateDownloadState.Idle),
        )
    }

    @Test
    fun `download progress is written into the primary button`() {
        assertEquals(
            "下载中 87%",
            updateDialogPrimaryText(hasApk = true, downloadState = downloading(87, 100)),
        )
        // 除不尽时截断而不是进位：未下满不能显示成 100%
        assertEquals(
            "下载中 33%",
            updateDialogPrimaryText(hasApk = true, downloadState = downloading(1, 3)),
        )
    }

    @Test
    fun `unknown total length reports zero instead of jumping`() {
        assertEquals(
            "下载中 0%",
            updateDialogPrimaryText(hasApk = true, downloadState = downloading(4_096, 0)),
        )
    }

    @Test
    fun `progress is clamped to a hundred percent`() {
        assertEquals(
            "下载中 100%",
            updateDialogPrimaryText(hasApk = true, downloadState = downloading(3, 2)),
        )
    }

    @Test
    fun `download card combines size with percent`() {
        // 总长未知时退回已下字节，百分比同样带百分号
        assertEquals("4.0 KB（0%）", updateProgressText(downloading(4_096, 0), withSize = true))
        assertEquals("2 B（100%）", updateProgressText(downloading(3, 2), withSize = true))
    }

    @Test
    fun `settled download returns to the download label`() {
        val file = File("/tmp/jm3-9.9.9-test.apk")
        assertEquals(
            "立即下载",
            updateDialogPrimaryText(hasApk = true, downloadState = UpdateDownloadState.Done(file)),
        )
        assertEquals(
            "立即下载",
            updateDialogPrimaryText(hasApk = true, downloadState = UpdateDownloadState.Failed("网络中断")),
        )
    }

    @Test
    fun `missing apk keeps the releases label even mid download`() {
        assertEquals(
            "前往 Releases 页面",
            updateDialogPrimaryText(hasApk = false, downloadState = downloading(87, 100)),
        )
    }

    @Test
    fun `parseInlineNotes ignores non-http markdown links`() {
        val samples = listOf(
            "[点我](javascript:alert(1))",
            "[本地](file:///sdcard/evil.apk)",
            "[跳转](intent://scan/#Intent;end)",
            "[邮箱](mailto:test@example.com)",
        )
        for (sample in samples) {
            val result = parseInlineNotes(
                text = sample,
                codeColor = Color(0xFF123456),
                linkColor = Color(0xFF3366FF),
                codeBackground = Color(0xFFEEEEEE),
            )
            assertTrue(result.getLinkAnnotations(0, result.length).isEmpty(), "Should not linkify: $sample")
            assertEquals(sample, result.text)
        }
    }

    @Test
    fun `parseInlineNotes keeps http and https markdown links`() {
        val https = parseInlineNotes(
            text = "见 [官网](https://example.com/a)",
            codeColor = Color(0xFF123456),
            linkColor = Color(0xFF3366FF),
            codeBackground = Color(0xFFEEEEEE),
        )
        assertEquals("见 官网", https.text)
        val httpsLink = assertIs<LinkAnnotation.Url>(https.getLinkAnnotations(0, https.length).single().item)
        assertEquals("https://example.com/a", httpsLink.url)

        val http = parseInlineNotes(
            text = "见 [镜像](http://mirror.example.com/b)",
            codeColor = Color(0xFF123456),
            linkColor = Color(0xFF3366FF),
            codeBackground = Color(0xFFEEEEEE),
        )
        assertEquals("见 镜像", http.text)
        val httpLink = assertIs<LinkAnnotation.Url>(http.getLinkAnnotations(0, http.length).single().item)
        assertEquals("http://mirror.example.com/b", httpLink.url)
    }
}
