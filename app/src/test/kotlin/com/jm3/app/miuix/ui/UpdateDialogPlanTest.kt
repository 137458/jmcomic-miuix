package com.jm3.app.miuix.ui

import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 更新弹窗的数据计划：弹窗只回答三件事——从哪个版本到哪个版本、更新说明是什么、下一步做什么。
 * 其余版本明细（体积、文件名、附件数量、说明行数）不进入计划，从数据层就杜绝再次堆回界面。
 */
class UpdateDialogPlanTest {

    private val shortRelease = ReleaseInfo(
        tagName = "v1.2.3",
        name = "常规版",
        notes = "## 更新\n- 第一项\n- 第二项",
        htmlUrl = "https://example.invalid/r",
        publishedAt = "2026-03-04T10:00:00Z",
        assets = listOf(
            ReleaseAsset("app-1.2.3.apk", 1024L * 1024, "https://example.invalid/app.apk"),
        ),
    )

    private val noApkRelease = ReleaseInfo(
        tagName = "v2.0.0-beta",
        name = "仅说明版",
        notes = "只有一行的说明。",
        htmlUrl = "https://example.invalid/r2",
        publishedAt = "not-a-date",
        assets = listOf(
            ReleaseAsset("notes.md", 2048L, "https://example.invalid/notes.md"),
        ),
    )

    private fun downloading(downloaded: Long, total: Long, speed: Long = 2_500_000L) =
        UpdateDownloadState.Downloading(downloaded, total, speed)

    @Test
    fun `summary merges version jump and publish date into one line`() {
        assertEquals("当前 1.1.0 → 最新 1.2.3 · 2026-03-04", updateDialogSummary("1.1.0", shortRelease))
    }

    @Test
    fun `summary drops the date when the release has none`() {
        assertEquals(
            "当前 1.1.0 → 最新 1.2.3",
            updateDialogSummary("1.1.0", shortRelease.copy(publishedAt = "  ")),
        )
    }

    @Test
    fun `summary keeps a non iso date as published`() {
        assertEquals("当前 1.1.0 → 最新 2.0.0-beta · not-a-date", updateDialogSummary("1.1.0", noApkRelease))
    }

    @Test
    fun `long fixture still collapses to a single version line`() {
        assertEquals(
            "当前 9.9.8 → 最新 9.9.9-test-long · 2026-06-18",
            updateDialogSummary("9.9.8", TEST_LONG_UPDATE_RELEASE),
        )
    }

    @Test
    fun `collapsed notes keep a bounded preview and count the rest`() {
        val all = "标题\n" + (1..20).joinToString("\n") { "第 " + it + " 行" }
        val state = updateDialogNotesState(all, expanded = false, previewLines = 5)
        assertEquals("标题\n第 1 行\n第 2 行\n第 3 行\n第 4 行", state.visibleMarkdown)
        assertEquals(16, state.hiddenLineCount)
    }

    @Test
    fun `expanded notes return the whole markdown without hidden lines`() {
        val all = "标题\n" + (1..20).joinToString("\n") { "第 " + it + " 行" }
        assertEquals(
            UpdateDialogNotesState(all, 0),
            updateDialogNotesState(all, expanded = true, previewLines = 5),
        )
    }

    @Test
    fun `short and blank notes never claim hidden lines`() {
        assertEquals(
            UpdateDialogNotesState("只有一行", 0),
            updateDialogNotesState("只有一行", expanded = false, previewLines = 12),
        )
        assertEquals(
            UpdateDialogNotesState("", 0),
            updateDialogNotesState("", expanded = false, previewLines = 12),
        )
        assertEquals(
            UpdateDialogNotesState("  ", 0),
            updateDialogNotesState("  ", expanded = false, previewLines = 12),
        )
    }

    @Test
    fun `preview is clamped to at least one line`() {
        val all = "A\nB\nC"
        assertEquals(
            UpdateDialogNotesState("A", 2),
            updateDialogNotesState(all, expanded = false, previewLines = 0),
        )
        assertEquals(
            UpdateDialogNotesState("A", 2),
            updateDialogNotesState(all, expanded = false, previewLines = -3),
        )
    }

    @Test
    fun `progress fraction is deterministic for known lengths and indeterminate otherwise`() {
        assertEquals(0.87f, updateDialogProgressFraction(downloading(87, 100)))
        assertEquals(1f / 3f, updateDialogProgressFraction(downloading(1, 3)))
        assertEquals(1f, updateDialogProgressFraction(downloading(3, 2)))
        assertNull(updateDialogProgressFraction(downloading(4_000_000, 0)))
        assertNull(updateDialogProgressFraction(downloading(4_000_000, -1)))
    }

    @Test
    fun `download detail reports bytes and speed on both known and unknown totals`() {
        assertEquals(
            "83.0 MB / 95.4 MB · 2.4 MB/s",
            updateDialogDownloadDetail(downloading(87L * 1_000_000, 100L * 1_000_000)),
        )
        assertEquals(
            "3.8 MB · 1.4 MB/s · 总长未知",
            updateDialogDownloadDetail(downloading(4L * 1_000_000, 0, 1_500_000L)),
        )
        assertEquals(
            "1.0 KB / 2.0 KB · 1.0 KB/s",
            updateDialogDownloadDetail(downloading(1024L, 2048L, 1024L)),
        )
    }

    @Test
    fun `idle plan is one primary plus dismiss, ignore optional`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease,
            downloadState = UpdateDownloadState.Idle,
            canIgnore = true,
            canCancelDownload = false,
        )
        assertEquals("立即下载", plan.primaryText)
        assertEquals("当前 1.1.0 → 最新 1.2.3 · 2026-03-04", plan.summary)
        assertFalse(plan.showProgress)
        assertNull(plan.progressFraction)
        assertNull(plan.progressDetail)
        assertTrue(plan.hasNotes)
        assertFalse(plan.showCancelDownload)
        assertTrue(plan.showIgnore)
    }

    @Test
    fun `downloading plan exposes progress and cancel, hides ignore`() {
        val state = downloading(87L * 1_000_000, 100L * 1_000_000)
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease,
            downloadState = state,
            canIgnore = true,
            canCancelDownload = true,
        )
        assertEquals("下载中 87%", plan.primaryText)
        assertTrue(plan.showProgress)
        assertEquals(0.87f, plan.progressFraction)
        assertEquals("83.0 MB / 95.4 MB · 2.4 MB/s", plan.progressDetail)
        assertTrue(plan.showCancelDownload)
        assertFalse(plan.showIgnore)
    }

    @Test
    fun `cancel only appears while a download is running`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease,
            downloadState = UpdateDownloadState.Idle,
            canIgnore = false,
            canCancelDownload = true,
        )
        assertFalse(plan.showCancelDownload)
        assertFalse(plan.showIgnore)
    }

    @Test
    fun `no apk keeps the releases fallback`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = noApkRelease,
            downloadState = UpdateDownloadState.Idle,
            canIgnore = true,
            canCancelDownload = true,
        )
        assertEquals("前往 Releases 页面", plan.primaryText)
        assertFalse(plan.showProgress)
    }

    @Test
    fun `empty notes remove the notes section`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease.copy(notes = ""),
            downloadState = UpdateDownloadState.Idle,
            canIgnore = true,
            canCancelDownload = false,
        )
        assertFalse(plan.hasNotes)
    }

    @Test
    fun `settled download returns to the primary action`() {
        val done = UpdateDownloadState.Done(File("/tmp/app.apk"))
        val failed = UpdateDownloadState.Failed("网络中断")
        assertEquals(
            "立即下载",
            buildUpdateDialogPlan("1.1.0", shortRelease, done, canIgnore = true, canCancelDownload = true).primaryText,
        )
        assertEquals(
            "立即下载",
            buildUpdateDialogPlan("1.1.0", shortRelease, failed, canIgnore = true, canCancelDownload = true).primaryText,
        )
    }
}
