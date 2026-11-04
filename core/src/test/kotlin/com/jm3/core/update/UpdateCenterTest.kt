package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * 检查更新的决策契约。
 *
 * 这里钉住的是两处调用点（启动期静默检查、更新页手动检查）共用的那条规则：
 * 远端比本地新时给出 [UpdateOutcome.Available]，是否真的要弹窗由 [UpdateOutcome.Available.notify]
 * 单独承载——已忽略本版本、或本进程已提示过本版本，都只能"有状态、不打扰"。
 * 传输层用注入的 `check` 替身，网络一次也不发。
 */
class UpdateCenterTest {

    private var checks = 0

    private fun release(tag: String) = ReleaseInfo(
        tagName = tag,
        name = "更新说明",
        notes = "- 修复若干问题",
        htmlUrl = "https://github.com/137458/jmcomic-miuix/releases/tag/$tag",
        publishedAt = "2026-10-31T06:12:34Z",
    )

    private fun center(result: UpdateCheckResult): UpdateCenter = UpdateCenter { localVersion ->
        checks++
        assertEquals("1.0.0", localVersion)
        result
    }

    private fun success(relation: VersionRelation, tag: String = "v1.2.0") =
        UpdateCheckResult.Success(release(tag), relation)

    @Test
    fun 新版本未忽略未提示时要求提醒() = runTest {
        val target = release("v1.2.0")
        val outcome = center(success(VersionRelation.NEWER)).evaluate("1.0.0", UpdatePromptState())
        assertEquals(UpdateOutcome.Available(target, notify = true), outcome)
    }

    @Test
    fun 忽略的是同一版本时给状态但不提醒() = runTest {
        val outcome = center(success(VersionRelation.NEWER))
            .evaluate("1.0.0", UpdatePromptState(ignoredVersion = "1.2.0"))
        assertEquals(UpdateOutcome.Available(release("v1.2.0"), notify = false), outcome)
    }

    @Test
    fun 本进程已提示过的版本不再提醒() = runTest {
        val outcome = center(success(VersionRelation.NEWER))
            .evaluate("1.0.0", UpdatePromptState(promptedVersion = "1.2.0"))
        assertEquals(UpdateOutcome.Available(release("v1.2.0"), notify = false), outcome)
    }

    @Test
    fun 忽略的是更早版本时新版本照常提醒() = runTest {
        val outcome = center(success(VersionRelation.NEWER))
            .evaluate("1.0.0", UpdatePromptState(ignoredVersion = "1.1.0", promptedVersion = "1.1.0"))
        assertEquals(UpdateOutcome.Available(release("v1.2.0"), notify = true), outcome)
    }

    @Test
    fun 远端与本地同版本时是已是最新() = runTest {
        val outcome = center(success(VersionRelation.SAME)).evaluate("1.0.0", UpdatePromptState())
        assertEquals(UpdateOutcome.UpToDate(release("v1.2.0"), localAhead = false), outcome)
    }

    @Test
    fun 本地版本更高时标记本地领先() = runTest {
        val outcome = center(success(VersionRelation.OLDER)).evaluate("1.0.0", UpdatePromptState())
        assertEquals(UpdateOutcome.UpToDate(release("v1.2.0"), localAhead = true), outcome)
    }

    @Test
    fun 检查失败保留原因与可直接展示的文案() = runTest {
        val outcome = center(UpdateCheckResult.Failed(UpdateFailure.NETWORK, "timeout"))
            .evaluate("1.0.0", UpdatePromptState())
        assertEquals(UpdateOutcome.Failed(UpdateFailure.NETWORK, "网络请求失败：timeout"), outcome)
    }

    @Test
    fun 一次检查只发一次请求() = runTest {
        center(success(VersionRelation.NEWER)).evaluate("1.0.0", UpdatePromptState())
        assertEquals(1, checks)
    }

    @Test
    fun 未开启自动检查时启动期连请求都不发() = runTest {
        val pending = center(success(VersionRelation.NEWER)).promptForStartup(
            autoCheckEnabled = false,
            localVersion = "1.0.0",
            prompt = UpdatePromptState(),
        )
        assertNull(pending)
        assertEquals(0, checks)
    }

    @Test
    fun 启动期只有未忽略的新版本才返回待弹窗内容() = runTest {
        val target = release("v1.2.0")
        val pending = center(success(VersionRelation.NEWER)).promptForStartup(
            autoCheckEnabled = true,
            localVersion = "1.0.0",
            prompt = UpdatePromptState(),
        )
        assertEquals(target, pending)
        assertEquals(1, checks)
    }

    @Test
    fun 启动期已提示过的版本不再返回() = runTest {
        val pending = center(success(VersionRelation.NEWER)).promptForStartup(
            autoCheckEnabled = true,
            localVersion = "1.0.0",
            prompt = UpdatePromptState(promptedVersion = "1.2.0"),
        )
        assertNull(pending)
    }

    @Test
    fun 启动期检查失败不打断启动() = runTest {
        val pending = center(UpdateCheckResult.Failed(UpdateFailure.NETWORK, "timeout"))
            .promptForStartup(true, "1.0.0", UpdatePromptState())
        assertNull(pending)
    }

    @Test
    fun 启动期已是最新时不弹窗() = runTest {
        val pending = center(success(VersionRelation.SAME))
            .promptForStartup(true, "1.0.0", UpdatePromptState())
        assertNull(pending)
    }

    @Test
    fun 启动期已忽略的新版本不弹窗() = runTest {
        val pending = center(success(VersionRelation.NEWER))
            .promptForStartup(true, "1.0.0", UpdatePromptState(ignoredVersion = "1.2.0"))
        assertNull(pending)
        assertTrue(checks == 1)
    }
}
