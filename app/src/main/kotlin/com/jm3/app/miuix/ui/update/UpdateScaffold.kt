package com.jm3.app.miuix.ui.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.openUpdateReleasePage
import com.jm3.app.miuix.ui.updateStatusText
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCenter
import com.jm3.core.update.UpdateOutcome
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.UpdatePromptState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 检查更新页的状态编排骨架：Miuix 与 COUI 两份皮肤共用同一套状态与动作真源。
 *
 * 本文件不含任何皮肤控件：只有不可变状态、纯文案/判定函数，以及一个负责 remember 与
 * LaunchedEffect 编排的入口。皮肤只保留视觉结构与 slot 传参。
 *
 * 取舍：检查结论仍由 core 的 [UpdateOutcome] 承载（null 表示正在检查），骨架只把
 * 「这次检查的编号、弹窗是否该出现、自动检查与忽略设置」编排成一个快照，
 * 避免两份皮肤各自维护一套会漂移的布尔量。
 */

/** 安装未知来源权限提示：两份皮肤原样共用。 */
internal const val UpdateInstallPermissionMessage: String = "请先允许本应用安装未知来源应用"

/** 拉起浏览器失败时的提示（带原始地址，便于用户手动复制）；COUI 皮肤改用自己的短句。 */
internal fun updateOpenReleaseFailureMessage(url: String): String = "无法打开链接：$url"

/** COUI 皮肤拉起浏览器失败时的提示。 */
internal const val UpdateNoBrowserMessage: String = "没有可用的浏览器"

/**
 * 一次安装动作对应的一次性提示；null 表示不需要提示（系统安装器已拉起）。
 *
 * [ApkInstaller.Result.Failed] 与 [ApkInstaller.Result.SignerMismatch] 都直接展示安装器给出的
 * 原因：签名不一致不可能靠重试解决，把原因摆给用户比让系统安装器弹一句装不上的错更有用。
 */
internal fun updateInstallMessage(result: ApkInstaller.Result): String? = when (result) {
    is ApkInstaller.Result.Launched -> null
    is ApkInstaller.Result.PermissionRequired -> UpdateInstallPermissionMessage
    is ApkInstaller.Result.Failed -> result.message
    is ApkInstaller.Result.SignerMismatch -> result.message
}

/** 检查更新页的页面状态：不可变快照，皮肤只读不改。 */
internal data class UpdateCheckState(
    val attempt: Int = 0,
    /** null 表示正在检查：与 [UpdateOutcome.Failed] 区分开，否则首次进入会闪一帧「失败」 */
    val outcome: UpdateOutcome? = null,
    val dialogVisible: Boolean = false,
    val autoCheck: Boolean = true,
    val ignoredVersion: String? = null,
) {
    val checking: Boolean get() = outcome == null

    val release: ReleaseInfo?
        get() = when (val current = outcome) {
            is UpdateOutcome.Available -> current.release
            is UpdateOutcome.UpToDate -> current.release
            else -> null
        }

    val latestVersion: String? get() = release?.version

    val hasNewVersion: Boolean get() = outcome is UpdateOutcome.Available

    /** 弹窗只对「有新版本」出现，已是最新与失败都不弹。 */
    val dialogRelease: ReleaseInfo? get() = (outcome as? UpdateOutcome.Available)?.release

    val ignored: Boolean get() = latestVersion?.let { UpdatePrompt.isIgnored(ignoredVersion, it) } ?: false

    val ignoreEnabled: Boolean get() = hasNewVersion && latestVersion != null

    /** 开始一次检查：先回到「正在检查」，并收起上一次的弹窗。 */
    fun reloadStarted(): UpdateCheckState = copy(outcome = null, dialogVisible = false)

    /** 手动重查：编号自增触发重新检查，同样先收起弹窗。 */
    fun attemptsNext(): UpdateCheckState =
        copy(attempt = attempt + 1, outcome = null, dialogVisible = false)

    /** 检查落定：只有判定「该提醒」时才弹窗。 */
    fun loaded(outcome: UpdateOutcome, notify: Boolean): UpdateCheckState =
        copy(outcome = outcome, dialogVisible = notify)

    fun dialogShown(): UpdateCheckState = copy(dialogVisible = true)

    fun dialogDismissed(): UpdateCheckState = copy(dialogVisible = false)

    fun autoCheckSet(enabled: Boolean): UpdateCheckState = copy(autoCheck = enabled)

    fun ignoredSet(version: String?): UpdateCheckState = copy(ignoredVersion = version)

    /** 弹窗里点「忽略此版本」：收起弹窗并直接落成该版本。 */
    fun ignoredDismissed(version: String): UpdateCheckState =
        copy(dialogVisible = false, ignoredVersion = version)
}

/**
 * 检查更新页的动作真源。
 *
 * 下载状态复用 [ScreenDeps.updateDownloader]，骨架只转发不另立状态机；持久化设置一律
 * 「先改内存态再异步落盘」，与皮肤原来的节奏一致（点击即生效，不等磁盘）。
 */
@Stable
internal class UpdateScaffoldState internal constructor(
    private val deps: ScreenDeps,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val context: Context,
    private val center: UpdateCenter,
    private val openReleaseFailure: (String) -> String,
    private val onPrompt: () -> Unit,
    val currentVersion: String,
    downloadStateSource: State<UpdateDownloadState>,
    initial: UpdateCheckState = UpdateCheckState(),
) {
    private var fields by mutableStateOf(initial)

    val attempt: Int get() = fields.attempt
    val outcome: UpdateOutcome? get() = fields.outcome
    val dialogVisible: Boolean get() = fields.dialogVisible
    val autoCheck: Boolean get() = fields.autoCheck
    val ignoredVersion: String? get() = fields.ignoredVersion
    val checking: Boolean get() = fields.checking
    val release: ReleaseInfo? get() = fields.release
    val latestVersion: String? get() = fields.latestVersion
    val hasNewVersion: Boolean get() = fields.hasNewVersion
    val dialogRelease: ReleaseInfo? get() = fields.dialogRelease
    val ignored: Boolean get() = fields.ignored
    val ignoreEnabled: Boolean get() = fields.ignoreEnabled
    val downloadState: State<UpdateDownloadState> = downloadStateSource

    /** 头部状态行文案：首屏检查中、失败原因与版本对照由 core 的纯函数给出。 */
    val statusText: String get() = updateStatusText(currentVersion, fields.outcome)

    /**
     * 读设置与检查放在同一个动作里：拆成两次调用时检查可能读到尚未加载的忽略版本，
     * 导致已忽略的版本又弹一次窗。
     */
    suspend fun reload() {
        fields = fields
            .autoCheckSet(deps.settings.autoCheckUpdate())
            .ignoredSet(deps.settings.ignoredUpdateVersion())
        fields = fields.reloadStarted()
        val outcome = center.evaluate(
            localVersion = currentVersion,
            prompt = UpdatePromptState(
                ignoredVersion = fields.ignoredVersion,
                promptedVersion = deps.promptedVersion,
            ),
        )
        // 只有"该提醒"时才弹窗并记下本次会话已提示：与启动期检查共用同一条规则，
        // 免得启动期弹过的版本进页面又被弹一次。记录只存在内存里，下次冷启动仍会提醒。
        val available = outcome as? UpdateOutcome.Available
        val notify = available != null && available.notify
        // 皮肤若要为「这次要弹窗」做点视觉准备（COUI 的预览弹窗要跟着收起），先让它动。
        if (notify) onPrompt()
        fields = fields.loaded(outcome, notify)
        if (notify && available != null) deps.promptedVersion = available.release.version
    }

    /** 手动重查（页面「重试」「手动检查更新」与顶栏刷新共用）。 */
    fun recheck() {
        fields = fields.attemptsNext()
    }

    fun dismissDialog() {
        fields = fields.dialogDismissed()
    }

    fun showDialog() {
        fields = fields.dialogShown()
    }

    fun setAutoCheck(enabled: Boolean) {
        fields = fields.autoCheckSet(enabled)
        scope.launch { deps.settings.setAutoCheckUpdate(enabled) }
    }

    fun setIgnoredVersion(version: String?) {
        fields = fields.ignoredSet(version)
        scope.launch { deps.settings.setIgnoredUpdateVersion(version) }
    }

    /** Miuix 皮肤弹窗的「忽略此版本」：直接落成该版本，不来回切换。 */
    fun ignoreVersion(version: String) {
        fields = fields.ignoredDismissed(version)
        scope.launch { deps.settings.setIgnoredUpdateVersion(version) }
    }

    /** COUI 皮肤弹窗的「忽略此版本」：同一版本不重复落盘，切换语义由 [UpdatePrompt] 给出。 */
    fun toggleIgnored(version: String) {
        setIgnoredVersion(UpdatePrompt.toggleIgnored(fields.ignoredVersion, version))
    }

    fun startDownload(release: ReleaseInfo) {
        release.apk?.let(::startDownload)
    }

    fun startDownload(asset: ReleaseAsset) {
        deps.updateDownloader.start(asset)
    }

    fun cancelDownload() {
        deps.updateDownloader.cancel()
    }

    fun resetDownload() {
        deps.updateDownloader.reset()
    }

    fun install(file: File) {
        val message = updateInstallMessage(deps.installUpdate(file)) ?: return
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun openRelease(url: String) {
        if (!openUpdateReleasePage(context, url)) {
            scope.launch { snackbarHostState.showSnackbar(openReleaseFailure(url)) }
        }
    }
}

/**
 * 皮肤入口：负责 remember 与 LaunchedEffect 编排。
 *
 * 差异用参数表达——打开链接失败的提示词（Miuix 带地址、COUI 只说没有浏览器）由调用方给。
 */
@Composable
internal fun rememberUpdateScaffoldState(
    deps: ScreenDeps,
    snackbarHostState: SnackbarHostState,
    currentVersion: String,
    openReleaseFailure: (String) -> String = ::updateOpenReleaseFailureMessage,
    onPrompt: () -> Unit = {},
): UpdateScaffoldState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val center = remember { UpdateCenter() }
    val downloadState = deps.updateDownloader.state.collectAsState()
    val state = remember(deps, center) {
        UpdateScaffoldState(
            deps = deps,
            scope = scope,
            snackbarHostState = snackbarHostState,
            context = context,
            center = center,
            openReleaseFailure = openReleaseFailure,
            onPrompt = onPrompt,
            currentVersion = currentVersion,
            downloadStateSource = downloadState,
        )
    }
    LaunchedEffect(state, state.attempt) {
        state.reload()
    }
    // 下载落定（完成或失败）就收起弹窗：安装与重试都在页面的下载卡上，弹窗只负责把进度显示完。
    // key 用布尔而不是整个 state，否则每个进度 tick 都会重启一次 effect。
    val settled = downloadState.value is UpdateDownloadState.Done ||
        downloadState.value is UpdateDownloadState.Failed
    LaunchedEffect(settled) {
        if (settled) state.dismissDialog()
    }
    return state
}
