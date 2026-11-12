package com.jm3.app.miuix.ui.coui.page

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateFailure
import com.jm3.core.update.UpdateOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CouiUpdateScreenPolicyTest {

    private val accent = Color(0xFF0A84FF)
    private val codeBg = Color(0xFFEEEEEE)

    private fun release(
        version: String = "v1.2.0",
        publishedAt: String = "2026-11-12T08:00:00Z",
        assetSize: Long = 12L * 1024 * 1024,
    ) = ReleaseInfo(
        tagName = version,
        name = "Release $version",
        notes = "- 修复 [链接](https://example.com)\n- 详情 https://example.com/docs",
        htmlUrl = "https://github.com/137458/jmcomic-miuix/releases/tag/$version",
        publishedAt = publishedAt,
        assets = if (assetSize > 0L) {
            listOf(ReleaseAsset("jm3-$version.apk", assetSize, "https://example.com/app.apk"))
        } else {
            emptyList()
        },
    )

    @Test
    fun `couiInlineNotes parses markdown links, bare urls, bold and code spans`() {
        val styled = couiInlineNotes(
            text = "发布说明 **重要** 见 [更新日志](https://example.com/1) 与 https://example.com/2、`tag`",
            accentColor = accent,
            codeBackground = codeBg,
        )

        assertEquals("发布说明 重要 见 更新日志 与 https://example.com/2、tag", styled.text)
        val links = styled.getLinkAnnotations(0, styled.length)
        assertEquals(2, links.size)
        assertEquals("https://example.com/1", assertIs<LinkAnnotation.Url>(links[0].item).url)
        assertEquals("https://example.com/2", assertIs<LinkAnnotation.Url>(links[1].item).url)
        assertTrue(styled.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(styled.spanStyles.any { it.item.background == codeBg })
    }

    @Test
    fun `couiChangelogDefaultExpanded expands new updates and collapses long up-to-date logs`() {
        val r = release()
        assertTrue(couiChangelogDefaultExpanded(UpdateOutcome.Available(r, notify = true), blockCount = 10))
        assertFalse(couiChangelogDefaultExpanded(UpdateOutcome.UpToDate(r, localAhead = false), blockCount = 6))
        assertTrue(couiChangelogDefaultExpanded(UpdateOutcome.UpToDate(r, localAhead = false), blockCount = 2))
    }

    @Test
    fun `couiUpdateButtonLabel follows download and availability states`() {
        val r = release()
        assertEquals("正在检测更新…", couiUpdateButtonLabel(null, UpdateDownloadState.Idle))
        assertEquals("下载并安装", couiUpdateButtonLabel(UpdateOutcome.Available(r, false), UpdateDownloadState.Idle))
        assertEquals(
            "正在下载… 45%",
            couiUpdateButtonLabel(
                UpdateOutcome.Available(r, false),
                UpdateDownloadState.Downloading(45L, 100L, 1024L),
            ),
        )
        assertEquals(
            "下载完成，立即安装",
            couiUpdateButtonLabel(
                UpdateOutcome.Available(r, false),
                UpdateDownloadState.Done(File("app.apk")),
            ),
        )
        assertEquals("重新检查更新", couiUpdateButtonLabel(UpdateOutcome.Failed(UpdateFailure.NETWORK, "err"), UpdateDownloadState.Idle))
        assertEquals("检查更新", couiUpdateButtonLabel(UpdateOutcome.UpToDate(r, localAhead = false), UpdateDownloadState.Idle))
    }

    @Test
    fun `couiUpdateReleaseMetaLine formats publish date and package size like oshin`() {
        val line = couiUpdateReleaseMetaLine(release(assetSize = 12L * 1024 * 1024))
        assertTrue(line.contains("2026-11-12"))
        assertTrue(line.contains("12.0 MB"))

        val noAsset = couiUpdateReleaseMetaLine(release(assetSize = 0L))
        assertEquals("发布于 2026-11-12", noAsset)
    }
}
