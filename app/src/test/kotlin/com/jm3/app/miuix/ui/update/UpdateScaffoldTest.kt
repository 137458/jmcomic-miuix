package com.jm3.app.miuix.ui.update

import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateFailure
import com.jm3.core.update.UpdateOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 检查更新页状态编排骨架的契约。
 *
 * 两份皮肤共用同一套状态与提示词，所以这里钉住三件事：检查三态（检查中/有新版本/失败）
 * 的派生值、弹窗「该出现才出现」的开关、以及安装结果到提示文案的一一对应。
 */
class UpdateScaffoldTest {

    private fun release(tag: String = "v1.3.0") = ReleaseInfo(
        tagName = tag,
        name = "JM3 $tag",
        notes = "- 修复若干问题",
        htmlUrl = "https://example.com/releases/$tag",
        publishedAt = "2026-11-01T08:00:00Z",
    )

    private fun loadedNewVersion(notify: Boolean, tag: String = "v1.3.0"): UpdateCheckState =
        UpdateCheckState()
            .reloadStarted()
            .loaded(UpdateOutcome.Available(release(tag), notify = notify), notify = notify)

    @Test
    fun `初始态停在做检查并默认开启自动检查`() {
        val idle = UpdateCheckState()

        assertEquals(0, idle.attempt)
        assertNull(idle.outcome)
        assertTrue(idle.checking)
        assertNull(idle.release)
        assertNull(idle.latestVersion)
        assertNull(idle.dialogRelease)
        assertFalse(idle.hasNewVersion)
        assertFalse(idle.dialogVisible)
        assertTrue(idle.autoCheck)
        assertNull(idle.ignoredVersion)
        assertFalse(idle.ignored)
        assertFalse(idle.ignoreEnabled)
    }

    @Test
    fun `重新检查会清空上次结论并收起弹窗`() {
        val next = loadedNewVersion(notify = true).reloadStarted()

        assertNull(next.outcome)
        assertTrue(next.checking)
        assertFalse(next.dialogVisible)
        assertNull(next.dialogRelease)
        assertEquals(0, next.attempt)
    }

    @Test
    fun `判定该提醒的新版本会弹出弹窗并携带发布信息`() {
        val state = loadedNewVersion(notify = true)

        assertFalse(state.checking)
        assertTrue(state.hasNewVersion)
        assertTrue(state.dialogVisible)
        assertEquals(release(), state.release)
        assertEquals(release(), state.dialogRelease)
        assertEquals("1.3.0", state.latestVersion)
        assertTrue(state.ignoreEnabled)
        assertFalse(state.ignored)
    }

    @Test
    fun `已被忽略或已提示过的版本只更新状态不弹窗`() {
        val state = loadedNewVersion(notify = false)

        assertTrue(state.hasNewVersion)
        assertEquals("1.3.0", state.latestVersion)
        assertFalse(state.dialogVisible)
    }

    @Test
    fun `已是最新版本保留发布说明但不提示更新`() {
        val state = UpdateCheckState()
            .reloadStarted()
            .loaded(UpdateOutcome.UpToDate(release(), localAhead = false), notify = false)

        assertFalse(state.checking)
        assertFalse(state.hasNewVersion)
        assertEquals(release(), state.release)
        assertEquals("1.3.0", state.latestVersion)
        assertNull(state.dialogRelease)
        assertFalse(state.dialogVisible)
        assertFalse(state.ignoreEnabled)
    }

    @Test
    fun `本地版本更高的已是最新态同样不提示更新`() {
        val state = UpdateCheckState()
            .reloadStarted()
            .loaded(UpdateOutcome.UpToDate(release("v1.2.0"), localAhead = true), notify = false)

        assertFalse(state.hasNewVersion)
        assertEquals("1.2.0", state.latestVersion)
        assertNull(state.dialogRelease)
    }

    @Test
    fun `检查失败只留失败原因且不提供忽略入口`() {
        val message = "网络连接失败，请检查网络后重试"
        val state = UpdateCheckState()
            .reloadStarted()
            .loaded(UpdateOutcome.Failed(UpdateFailure.NETWORK, message), notify = false)

        assertFalse(state.checking)
        assertFalse(state.hasNewVersion)
        assertNull(state.release)
        assertNull(state.latestVersion)
        assertNull(state.dialogRelease)
        assertFalse(state.ignoreEnabled)
        assertFalse(state.dialogVisible)
        assertEquals(message, (state.outcome as UpdateOutcome.Failed).message)
        assertEquals(UpdateFailure.NETWORK, (state.outcome as UpdateOutcome.Failed).failure)
    }

    @Test
    fun `弹窗可以收起再重新弹出`() {
        val loaded = loadedNewVersion(notify = true)
        val dismissed = loaded.dialogDismissed()

        assertFalse(dismissed.dialogVisible)
        assertTrue(dismissed.hasNewVersion)
        assertTrue(dismissed.dialogShown().dialogVisible)
    }

    @Test
    fun `手动重查让检查编号自增并回到检查中`() {
        val rechecking = loadedNewVersion(notify = true).attemptsNext()

        assertEquals(1, rechecking.attempt)
        assertNull(rechecking.outcome)
        assertTrue(rechecking.checking)
        assertFalse(rechecking.dialogVisible)
        assertEquals(2, rechecking.attemptsNext().attempt)
    }

    @Test
    fun `忽略过某版本后只有同版本被判为已忽略`() {
        val state = loadedNewVersion(notify = true)

        assertTrue(state.ignoredSet("1.3.0").ignored)
        assertFalse(state.ignoredSet("1.2.0").ignored)
        assertFalse(state.ignoredSet(null).ignored)
    }

    @Test
    fun `弹窗里忽略当前版本会收起弹窗并记下该版本`() {
        val state = loadedNewVersion(notify = true).ignoredDismissed("1.3.0")

        assertFalse(state.dialogVisible)
        assertEquals("1.3.0", state.ignoredVersion)
        assertTrue(state.ignored)
        assertTrue(state.hasNewVersion)
    }

    @Test
    fun `自动检查开关只改开关不影响检查结论`() {
        val state = loadedNewVersion(notify = true)

        assertFalse(state.autoCheckSet(false).autoCheck)
        assertTrue(state.autoCheckSet(true).autoCheck)
        assertTrue(state.autoCheckSet(false).hasNewVersion)
    }

    @Test
    fun `系统安装器拉起时无需任何提示`() {
        assertNull(updateInstallMessage(ApkInstaller.Result.Launched))
    }

    @Test
    fun `缺少安装未知来源权限时提示去授权`() {
        assertEquals(
            "请先允许本应用安装未知来源应用",
            updateInstallMessage(ApkInstaller.Result.PermissionRequired),
        )
    }

    @Test
    fun `安装失败原样展示安装器给出的原因`() {
        assertEquals(
            "安装包不存在，请重新下载",
            updateInstallMessage(ApkInstaller.Result.Failed("安装包不存在，请重新下载")),
        )
    }

    @Test
    fun `签名不一致原样展示停止安装的原因`() {
        val message = "安装包签名与当前应用不一致（发布者不同），已停止安装；如需换装须先卸载，本地数据会被清空"

        assertEquals(message, updateInstallMessage(ApkInstaller.Result.SignerMismatch(message)))
    }

    @Test
    fun `打开链接失败的提示按皮肤取词`() {
        assertEquals(
            "无法打开链接：https://example.com/releases/v1.3.0",
            updateOpenReleaseFailureMessage("https://example.com/releases/v1.3.0"),
        )
        assertEquals("没有可用的浏览器", UpdateNoBrowserMessage)
    }
}
