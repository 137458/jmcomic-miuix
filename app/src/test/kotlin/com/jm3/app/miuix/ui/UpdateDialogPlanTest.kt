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
 * 更新弹窗的数据计划：把「弹窗里有哪些信息、哪些操作、进度条怎么显示」收成纯函数，
 * 这样 Miuix 组件只负责铺像素，长日志折叠与下载态切换都能脱离设备验证。
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
    fun `info rows expose version metadata for a normal release`() {
        assertEquals(
            listOf(
                UpdateDialogInfoRow("当前版本", "1.1.0"),
                UpdateDialogInfoRow("最新版本", "1.2.3"),
                UpdateDialogInfoRow("版本名称", "常规版"),
                UpdateDialogInfoRow("发布日期", "2026-03-04"),
                UpdateDialogInfoRow("安装包", "app-1.2.3.apk"),
                UpdateDialogInfoRow("安装包大小", "1.0 MB"),
                UpdateDialogInfoRow("附件数量", "1"),
                UpdateDialogInfoRow("更新说明", "3 行"),
            ),
            updateDialogInfoRows("1.1.0", shortRelease),
        )
    }

    @Test
    fun `info rows stay complete when the release has no apk`() {
        assertEquals(
            listOf(
                UpdateDialogInfoRow("当前版本", "1.1.0"),
                UpdateDialogInfoRow("最新版本", "2.0.0-beta"),
                UpdateDialogInfoRow("版本名称", "仅说明版"),
                UpdateDialogInfoRow("发布日期", "not-a-date"),
                UpdateDialogInfoRow("安装包", "未附带 APK"),
                UpdateDialogInfoRow("安装包大小", "未知"),
                UpdateDialogInfoRow("附件数量", "1"),
                UpdateDialogInfoRow("更新说明", "1 行"),
            ),
            updateDialogInfoRows("1.1.0", noApkRelease),
        )
    }

    @Test
    fun `long fixture selects the universal apk and reports every asset`() {
        val rows = updateDialogInfoRows("9.9.8", TEST_LONG_UPDATE_RELEASE).associate { it.label to it.value }
        assertEquals("9.9.9-test-long", rows["最新版本"])
        assertEquals("长日志排版验证版", rows["版本名称"])
        assertEquals("2026-06-18", rows["发布日期"])
        assertEquals("jm3-9.9.9-test-universal.apk", rows["安装包"])
        assertEquals("45.8 MB", rows["安装包大小"])
        assertEquals("4", rows["附件数量"])
        assertTrue((rows["更新说明"] ?: "").startsWith("6"))
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
            "1 KB / 2 KB · 1 KB/s",
            updateDialogDownloadDetail(downloading(1024L, 2048L, 1024L)),
        )
    }

    @Test
    fun `idle plan offers every secondary action and no progress`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease,
            downloadState = UpdateDownloadState.Idle,
            canIgnore = true,
            canCancelDownload = false,
            canOpenUpdatePage = true,
        )
        assertEquals("立即下载", plan.primaryText)
        assertFalse(plan.showProgress)
        assertNull(plan.progressFraction)
        assertNull(plan.progressDetail)
        assertTrue(plan.hasNotes)
        assertEquals(
            listOf(
                UpdateDialogActionItem(UpdateDialogAction.IGNORE_VERSION),
                UpdateDialogActionItem(UpdateDialogAction.COPY_NOTES),
                UpdateDialogActionItem(UpdateDialogAction.OPEN_RELEASE),
                UpdateDialogActionItem(UpdateDialogAction.OPEN_UPDATE_PAGE),
                UpdateDialogActionItem(UpdateDialogAction.DISMISS),
            ),
            plan.actions,
        )
        assertEquals(8, plan.infoRows.size)
    }

    @Test
    fun `downloading plan exposes cancel and disables ignore`() {
        val state = downloading(87L * 1_000_000, 100L * 1_000_000)
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease,
            downloadState = state,
            canIgnore = true,
            canCancelDownload = true,
            canOpenUpdatePage = true,
        )
        assertEquals("下载中 87%", plan.primaryText)
        assertTrue(plan.showProgress)
        assertEquals(0.87f, plan.progressFraction)
        assertEquals("83.0 MB / 95.4 MB · 2.4 MB/s", plan.progressDetail)
        assertEquals(
            listOf(
                UpdateDialogActionItem(UpdateDialogAction.CANCEL_DOWNLOAD),
                UpdateDialogActionItem(UpdateDialogAction.IGNORE_VERSION, enabled = false),
                UpdateDialogActionItem(UpdateDialogAction.COPY_NOTES),
                UpdateDialogActionItem(UpdateDialogAction.OPEN_RELEASE),
                UpdateDialogActionItem(UpdateDialogAction.OPEN_UPDATE_PAGE),
                UpdateDialogActionItem(UpdateDialogAction.DISMISS),
            ),
            plan.actions,
        )
    }

    @Test
    fun `no apk keeps the releases fallback and omits cancel`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = noApkRelease,
            downloadState = UpdateDownloadState.Idle,
            canIgnore = true,
            canCancelDownload = true,
            canOpenUpdatePage = false,
        )
        assertEquals("前往 Releases 页面", plan.primaryText)
        assertFalse(plan.showProgress)
        assertEquals(
            listOf(
                UpdateDialogActionItem(UpdateDialogAction.IGNORE_VERSION),
                UpdateDialogActionItem(UpdateDialogAction.COPY_NOTES),
                UpdateDialogActionItem(UpdateDialogAction.OPEN_RELEASE),
                UpdateDialogActionItem(UpdateDialogAction.DISMISS),
            ),
            plan.actions,
        )
    }

    @Test
    fun `plan omits actions whose callbacks are unavailable`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease,
            downloadState = UpdateDownloadState.Idle,
            canIgnore = false,
            canCancelDownload = false,
            canOpenUpdatePage = false,
        )
        assertEquals(
            listOf(
                UpdateDialogActionItem(UpdateDialogAction.COPY_NOTES),
                UpdateDialogActionItem(UpdateDialogAction.OPEN_RELEASE),
                UpdateDialogActionItem(UpdateDialogAction.DISMISS),
            ),
            plan.actions,
        )
        assertFalse(plan.actions.any { it.action == UpdateDialogAction.IGNORE_VERSION })
    }

    @Test
    fun `empty notes remove the notes section`() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.1.0",
            release = shortRelease.copy(notes = ""),
            downloadState = UpdateDownloadState.Idle,
            canIgnore = true,
            canCancelDownload = false,
            canOpenUpdatePage = true,
        )
        assertFalse(plan.hasNotes)
    }

    @Test
    fun `settled download returns to the primary action`() {
        val done = UpdateDownloadState.Done(File("/tmp/app.apk"))
        val failed = UpdateDownloadState.Failed("网络中断")
        assertEquals("立即下载", buildUpdateDialogPlan("1.1.0", shortRelease, done, true, true, true).primaryText)
        assertEquals("立即下载", buildUpdateDialogPlan("1.1.0", shortRelease, failed, true, true, true).primaryText)
    }

    @Test
    fun `action labels are stable and copy label reflects clipboard state`() {
        assertEquals("取消下载", updateDialogActionLabel(UpdateDialogAction.CANCEL_DOWNLOAD, false))
        assertEquals("忽略此版本", updateDialogActionLabel(UpdateDialogAction.IGNORE_VERSION, false))
        assertEquals("在浏览器查看完整说明", updateDialogActionLabel(UpdateDialogAction.OPEN_RELEASE, false))
        assertEquals("打开更新页面", updateDialogActionLabel(UpdateDialogAction.OPEN_UPDATE_PAGE, false))
        assertEquals("稍后", updateDialogActionLabel(UpdateDialogAction.DISMISS, false))
        assertEquals("复制更新说明", updateDialogActionLabel(UpdateDialogAction.COPY_NOTES, noteCopied = false))
        assertEquals("已复制说明", updateDialogActionLabel(UpdateDialogAction.COPY_NOTES, noteCopied = true))
    }
}
