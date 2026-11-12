package com.jm3.app.miuix.ui.coui.page

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.jm3.app.miuix.ui.coui.component.couiFloatingSurfaceEnabled
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdateFailure
import com.jm3.core.update.UpdateOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun `couiUpdateButtonLabel falls back to downloaded bytes when total length is unknown`() {
        val r = release()
        assertEquals(
            "正在下载… 4.0 KB",
            couiUpdateButtonLabel(
                UpdateOutcome.Available(r, false),
                UpdateDownloadState.Downloading(4096L, 0L, 1024L),
            ),
        )
    }

    @Test
    fun `couiUpdateHeadline and versionLine distinguish localAhead from regular up-to-date`() {
        val r = release("v1.2.0")
        assertEquals("正在检测更新", couiUpdateHeadline(null))
        assertEquals("发现新版本", couiUpdateHeadline(UpdateOutcome.Available(r, false)))
        assertEquals("检查更新失败", couiUpdateHeadline(UpdateOutcome.Failed(UpdateFailure.NETWORK, "err")))
        assertEquals("已是最新版本", couiUpdateHeadline(UpdateOutcome.UpToDate(r, localAhead = false)))
        assertEquals("当前版本已领先", couiUpdateHeadline(UpdateOutcome.UpToDate(r, localAhead = true)))

        assertEquals("软件版本  v1.1.0 → v1.2.0", couiUpdateVersionLine("1.1.0", UpdateOutcome.Available(r, false)))
        assertEquals("软件版本  v1.2.0", couiUpdateVersionLine("1.2.0", UpdateOutcome.UpToDate(r, localAhead = false)))
        assertEquals("本地 v1.3.0 · 远端 v1.2.0", couiUpdateVersionLine("1.3.0", UpdateOutcome.UpToDate(r, localAhead = true)))
    }

    @Test
    fun `couiUpdateChangelogTitle differentiates available, up-to-date, and localAhead`() {
        val r = release("v1.2.0")
        assertEquals("更新说明（1.2.0）", couiUpdateChangelogTitle(UpdateOutcome.Available(r, false)))
        assertEquals("当前版本说明（1.2.0）", couiUpdateChangelogTitle(UpdateOutcome.UpToDate(r, localAhead = false)))
        assertEquals("最新公开发布说明（1.2.0）", couiUpdateChangelogTitle(UpdateOutcome.UpToDate(r, localAhead = true)))
        assertEquals("更新说明", couiUpdateChangelogTitle(null))
    }

    @Test
    fun `couiUpdateProgressFraction returns indeterminate null when total unknown`() {
        assertEquals(0f, couiUpdateProgressFraction(UpdateDownloadState.Idle))
        assertEquals(0.45f, couiUpdateProgressFraction(UpdateDownloadState.Downloading(45L, 100L, 512L)))
        assertNull(couiUpdateProgressFraction(UpdateDownloadState.Downloading(4096L, 0L, 512L)))
        assertEquals(1f, couiUpdateProgressFraction(UpdateDownloadState.Done(File("app.apk"))))
        assertEquals(0f, couiUpdateProgressFraction(UpdateDownloadState.Failed("err")))
    }

    @Test
    fun `couiUpdateActionBarMode maps checking, download states, and outcomes accurately`() {
        val r = release("v1.2.0")
        assertEquals(CouiUpdateActionBarMode.CHECKING, couiUpdateActionBarMode(null, UpdateDownloadState.Idle))
        assertEquals(
            CouiUpdateActionBarMode.DOWNLOADING,
            couiUpdateActionBarMode(UpdateOutcome.Available(r, false), UpdateDownloadState.Downloading(1L, 2L, 1L)),
        )
        assertEquals(
            CouiUpdateActionBarMode.READY_TO_INSTALL,
            couiUpdateActionBarMode(UpdateOutcome.Available(r, false), UpdateDownloadState.Done(File("a.apk"))),
        )
        assertEquals(
            CouiUpdateActionBarMode.DOWNLOAD_FAILED,
            couiUpdateActionBarMode(UpdateOutcome.Available(r, false), UpdateDownloadState.Failed("err")),
        )
        assertEquals(
            CouiUpdateActionBarMode.AVAILABLE_IDLE,
            couiUpdateActionBarMode(UpdateOutcome.Available(r, false), UpdateDownloadState.Idle),
        )
        assertEquals(
            CouiUpdateActionBarMode.CHECK_FAILED,
            couiUpdateActionBarMode(UpdateOutcome.Failed(UpdateFailure.NETWORK, "err"), UpdateDownloadState.Idle),
        )
        assertEquals(
            CouiUpdateActionBarMode.UP_TO_DATE,
            couiUpdateActionBarMode(UpdateOutcome.UpToDate(r, localAhead = false), UpdateDownloadState.Idle),
        )
    }

    @Test
    fun `couiFloatingSurfaceEnabled requires API 34 and valid backdrop`() {
        assertFalse(couiFloatingSurfaceEnabled(sdkInt = 33, hasBackdrop = true))
        assertFalse(couiFloatingSurfaceEnabled(sdkInt = 34, hasBackdrop = false))
        assertTrue(couiFloatingSurfaceEnabled(sdkInt = 34, hasBackdrop = true))
        assertTrue(couiFloatingSurfaceEnabled(sdkInt = 36, hasBackdrop = true))
    }

    @Test
    fun `couiNextPreviewVersion bumps patch across semver and non-semver strings`() {
        assertEquals("1.1.1", couiNextPreviewVersion("1.1.0"))
        assertEquals("2.4.10", couiNextPreviewVersion("v2.4.9"))
        assertEquals("1.2.1", couiNextPreviewVersion("v1.2"))
        assertEquals("dev-preview", couiNextPreviewVersion("dev"))
        assertEquals("1.0.1", couiNextPreviewVersion(""))
    }

    @Test
    fun `couiPreviewUpdateRelease builds expandable notes and apk fallback even when offline`() {
        val offlinePreview = couiPreviewUpdateRelease(currentVersion = "1.1.0", remoteRelease = null)
        assertEquals("1.1.1", offlinePreview.version)
        assertTrue(offlinePreview.notes.lines().size >= 8)
        assertEquals(UpdateChecker.RELEASES_PAGE_URL, offlinePreview.htmlUrl)
        assertNotNull(offlinePreview.apk)

        val shortRemote = ReleaseInfo(
            tagName = "v1.1.0",
            name = "1.1.0",
            notes = "简短说明",
            htmlUrl = "https://github.com/137458/jmcomic-miuix/releases/tag/v1.1.0",
            publishedAt = "2026-11-10T00:00:00Z",
            assets = emptyList(),
        )
        val enrichedPreview = couiPreviewUpdateRelease(currentVersion = "1.1.0", remoteRelease = shortRemote)
        assertEquals("1.1.1", enrichedPreview.version)
        assertTrue(enrichedPreview.notes.lines().size >= 8)
        assertTrue(enrichedPreview.notes.contains("简短说明"))
        assertEquals(shortRemote.htmlUrl, enrichedPreview.htmlUrl)
        assertNotNull(enrichedPreview.apk)
    }
}
