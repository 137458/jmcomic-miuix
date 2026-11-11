// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.formatUpdateSize
import com.jm3.app.miuix.ui.formatUpdateSpeed
import com.jm3.app.miuix.ui.loadCurrentVersion
import com.jm3.app.miuix.ui.openUpdateReleasePage
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.updateBrowserFallbackUrl
import com.jm3.app.miuix.ui.updateProgressText
import com.jm3.app.miuix.ui.updateStatusText
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCenter
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdateOutcome
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.UpdatePromptState
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 更新说明的块间距，与更新弹窗保持同一节奏。 */
private val NotesBlockSpacing = 6.dp

/** 说明项前缀列的最小宽度：让多位数序号与项目符号不挤在一起。 */
private val NotesPrefixMinWidth = 20.dp

/**
 * COUI 检查更新页：一列详情卡，签名与原页面一致。
 *
 * 检查逻辑原样复用 [UpdateCenter] 与 [UpdatePrompt]：页面只负责状态落位与渲染。
 * 库侧没有进度条与开关行以外的表单控件，进度条按胶囊自绘，其余动作走卡片行。
 */
@Composable
internal fun CouiUpdateScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val center = remember { UpdateCenter() }
    val currentVersion = remember(context) { loadCurrentVersion(context) }
    val appName = remember(context) {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }

    var attempt by remember { mutableIntStateOf(0) }
    // null 表示正在检查：与 UpdateOutcome.Failed 区分开，否则首次进入会闪一帧「失败」
    var state by remember { mutableStateOf<UpdateOutcome?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var autoCheckUpdate by remember { mutableStateOf(false) }
    var ignoredVersion by remember { mutableStateOf<String?>(null) }
    val downloadState by deps.updateDownloader.state.collectAsState()

    val release = when (val current = state) {
        is UpdateOutcome.Available -> current.release
        is UpdateOutcome.UpToDate -> current.release
        else -> null
    }
    val hasNewVersion = state is UpdateOutcome.Available
    val latestVersion = release?.version
    val ignored = latestVersion?.let { UpdatePrompt.isIgnored(ignoredVersion, it) } ?: false

    LaunchedEffect(attempt) {
        autoCheckUpdate = deps.settings.autoCheckUpdate()
        ignoredVersion = deps.settings.ignoredUpdateVersion()
        state = null
        showDialog = false
        val outcome = center.evaluate(
            localVersion = currentVersion,
            prompt = UpdatePromptState(
                ignoredVersion = ignoredVersion,
                promptedVersion = deps.updates.promptedVersion,
            ),
        )
        state = outcome
        // 启动时被判定「该提示」才弹窗；被忽略的版本不再打扰，只在页面里留入口
        if (outcome is UpdateOutcome.Available && outcome.notify) {
            showDialog = true
            deps.updates.promptedVersion = outcome.release.version
        }
    }

    // 下载有了结论就收起弹窗：成功进安装、失败回页面看原因，留在弹窗里会挡住状态
    val downloadSettled = downloadState is UpdateDownloadState.Done ||
        downloadState is UpdateDownloadState.Failed
    LaunchedEffect(downloadSettled) {
        if (downloadSettled) showDialog = false
    }

    fun setAutoCheck(enabled: Boolean) {
        autoCheckUpdate = enabled
        scope.launch { deps.settings.setAutoCheckUpdate(enabled) }
    }

    fun toggleIgnored(version: String) {
        val next = UpdatePrompt.toggleIgnored(ignoredVersion, version)
        ignoredVersion = next
        scope.launch { deps.settings.setIgnoredUpdateVersion(next) }
    }

    fun openRelease(url: String) {
        if (!openUpdateReleasePage(context, url)) {
            scope.launch { snackbarHostState.showSnackbar("没有可用的浏览器") }
        }
    }

    fun install(file: File) {
        when (val result = ApkInstaller.install(context, file)) {
            is ApkInstaller.Result.Launched -> Unit
            is ApkInstaller.Result.PermissionRequired -> scope.launch {
                snackbarHostState.showSnackbar("请先允许本应用安装未知来源应用")
            }
            is ApkInstaller.Result.Failed -> scope.launch {
                snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    val ignoreSummary = when {
        state == null -> "正在检查更新…"
        latestVersion == null -> "尚未获取远端版本"
        ignored -> "已忽略" + latestVersion + "，不再弹出该版本提示"
        !hasNewVersion -> "当前没有需要忽略的新版本"
        else -> "忽略" + latestVersion + " 的更新提示"
    }

    CouiTheme {
        val scrollState = rememberScrollState()
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    LargeTopAppBar(
                        title = { CouiTopBarTitle(text = "检查更新", textAlign = TextAlign.Start) },
                        navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                        actions = {
                            CouiTopBarAction(
                                icon = CouiIcons.Refresh,
                                contentDescription = "重新检查",
                                onClick = { attempt++ },
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = couiTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .couiVerticalScroll(scrollState)
                        .padding(scrollBehavior.state.contentPadding(innerPadding, LocalDensity.current))
                        .consumeWindowInsets(innerPadding),
                ) {
                    UpdateVersionGroup(
                        appName = appName,
                        currentVersion = currentVersion,
                        statusText = updateStatusText(currentVersion, state),
                        release = release,
                        checking = state == null,
                    )
                    UpdateNotesGroup(
                        title = changelogTitle(state),
                        state = state,
                        downloadState = downloadState,
                        onRetryCheck = { attempt++ },
                        onOpenRelease = ::openRelease,
                        onStartDownload = { asset -> deps.updateDownloader.start(asset) },
                        onCancelDownload = { deps.updateDownloader.cancel() },
                        onResetDownload = { deps.updateDownloader.reset() },
                        onInstall = ::install,
                    )
                    UpdateSettingsGroup(
                        autoCheckUpdate = autoCheckUpdate,
                        ignoreSummary = ignoreSummary,
                        ignoreEnabled = hasNewVersion && latestVersion != null,
                        onAutoCheckChange = ::setAutoCheck,
                        onIgnoreToggle = { latestVersion?.let(::toggleIgnored) },
                    )
                    UpdateActionsGroup(
                        currentVersion = currentVersion,
                        release = release,
                        checking = state == null,
                        onRecheck = { attempt++ },
                        onOpenRelease = { openRelease(UpdateChecker.RELEASES_PAGE_URL) },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        // 弹窗常驻组合，只切 visible：库要靠它保留退场动画窗口
        UpdateAvailableDialog(
            show = showDialog,
            currentVersion = currentVersion,
            release = release,
            downloadState = downloadState,
            onDismiss = { showDialog = false },
            onDownload = { asset -> deps.updateDownloader.start(asset) },
            onCancelDownload = { deps.updateDownloader.cancel() },
            onResetDownload = { deps.updateDownloader.reset() },
            onInstall = ::install,
            onOpenRelease = ::openRelease,
            onIgnore = { version ->
                if (ignoredVersion != version) toggleIgnored(version)
                showDialog = false
            },
        )
    }
}

/** 第一张卡：应用身份 + 检查结论 + 远端版本，替代旧页的头图 hero。 */
@Composable
private fun UpdateVersionGroup(
    appName: String,
    currentVersion: String,
    statusText: String,
    release: ReleaseInfo?,
    checking: Boolean,
) {
    Group(modifier = Modifier.padding(top = 8.dp), title = "版本信息") {
        Item(
            title = { Text(text = appName, kind = CouiTextKind.PreferenceTitle) },
            summary = { Text(text = statusText, kind = CouiTextKind.PreferenceSummary) },
            trailing = {
                Text(text = checkingAlt(checking, "检查中…", currentVersion), kind = CouiTextKind.Description)
            },
        )
        Divider()
        Item(
            title = { Text(text = "远端最新版本", kind = CouiTextKind.PreferenceTitle) },
            summary = {
                val date = release?.publishedDate.orEmpty()
                Text(
                    text = when {
                        release == null -> "尚未获取"
                        date.isNotBlank() -> "发布于" + date
                        else -> "已获取最新发布信息"
                    },
                    kind = CouiTextKind.PreferenceSummary,
                )
            },
            trailing = {
                Text(
                    text = release?.version ?: "—",
                    kind = CouiTextKind.PreferenceValue,
                )
            },
        )
    }
}

/** 检查中不为版本号让位：避免一次检查把「当前版本」换成转圈文案后回不去。 */
private fun checkingAlt(checking: Boolean, whenChecking: String, otherwise: String): String =
    if (checking) whenChecking else otherwise

/** 更新日志卡：说明 + 下载区。失败与检查中同卡展示，重试入口不再另开一块。 */
@Composable
private fun UpdateNotesGroup(
    title: String,
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onRetryCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onStartDownload: (com.jm3.core.update.ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group(modifier = Modifier.padding(top = 8.dp), title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CouiPageDefaults.rowPadding,
                    vertical = CouiPageDefaults.contentPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(NotesBlockSpacing),
        ) {
            when (state) {
                null -> Text(text = "正在检查更新…", kind = CouiTextKind.Body)
                is UpdateOutcome.Failed -> {
                    Text(
                        text = state.message,
                        kind = CouiTextKind.Body,
                        color = scheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CouiInlineButton(text = "重试", onClick = onRetryCheck)
                        CouiInlineButton(
                            text = "在浏览器打开 Releases 页",
                            onClick = { onOpenRelease(UpdateChecker.RELEASES_PAGE_URL) },
                        )
                    }
                }
                is UpdateOutcome.UpToDate -> {
                    Text(
                        text = state.release.name.ifBlank { "版本特性说明" },
                        kind = CouiTextKind.PreferenceTitle,
                    )
                    UpdateNotes(markdown = state.release.notes)
                }
                is UpdateOutcome.Available -> {
                    Text(
                        text = state.release.name.ifBlank { "版本特性说明" },
                        kind = CouiTextKind.PreferenceTitle,
                    )
                    UpdateNotes(markdown = state.release.notes)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CouiPageDefaults.dividerHeight)
                            .background(scheme.outlineVariant),
                    )
                    UpdateDownloadBlock(
                        release = state.release,
                        downloadState = downloadState,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onResetDownload = onResetDownload,
                        onInstall = onInstall,
                        onOpenRelease = onOpenRelease,
                    )
                }
            }
        }
    }
}

/** 下载区：四种下载态各给一条主行动，避免用户只能干等。 */
@Composable
private fun UpdateDownloadBlock(
    release: ReleaseInfo,
    downloadState: UpdateDownloadState,
    onStartDownload: (com.jm3.core.update.ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val asset = release.apk
    if (asset == null) {
        Text(text = "该版本未附带安装包，请前往 Releases 页面手动下载。", kind = CouiTextKind.PreferenceSummary)
        CouiInlineButton(text = "前往 Releases 页面", onClick = { onOpenRelease(release.htmlUrl) })
        return
    }
    Text(
        text = "安装包 " + asset.name + assetSizeSuffix(asset.size),
        kind = CouiTextKind.Description,
        color = scheme.onSurfaceVariant,
    )
    when (downloadState) {
        is UpdateDownloadState.Idle -> CouiInlineButton(
            text = "立即下载更新",
            emphasised = true,
            onClick = { onStartDownload(asset) },
        )
        is UpdateDownloadState.Downloading -> {
            Text(
                text = "正在下载 " + updateProgressText(downloadState, withSize = true),
                kind = CouiTextKind.Body,
            )
            CouiProgressBar(progress = downloadState.progress)
            Text(
                text = if (downloadState.speedBytesPerSec > 0L) {
                    "速度 " + formatUpdateSpeed(downloadState.speedBytesPerSec)
                } else {
                    "正在计算速度…"
                },
                kind = CouiTextKind.Description,
                color = scheme.onSurfaceVariant,
            )
            CouiInlineButton(text = "取消下载", onClick = onCancelDownload)
        }
        is UpdateDownloadState.Done -> {
            Text(text = "安装包已下载完成，点击安装完成升级。", kind = CouiTextKind.Body)
            CouiInlineButton(text = "立即安装", emphasised = true, onClick = { onInstall(downloadState.file) })
            CouiInlineButton(text = "重新下载", onClick = onResetDownload)
        }
        is UpdateDownloadState.Failed -> {
            Text(
                text = downloadState.message,
                kind = CouiTextKind.Body,
                color = MiuixTheme.colorScheme.error,
            )
            CouiInlineButton(text = "重试", emphasised = true, onClick = { onResetDownload() })
            CouiInlineButton(
                text = "用浏览器下载",
                onClick = { onOpenRelease(updateBrowserFallbackUrl(release)) },
            )
        }
    }
}

/** 自绘进度条：库没有线性进度组件，胶囊轨道 + 强调色填充即可覆盖唯一用途。 */
@Composable
private fun CouiProgressBar(progress: Float) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CouiCapsuleShape)
            .background(scheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CouiCapsuleShape)
                .background(scheme.accent),
        )
    }
}

/** 更新说明正文：只按块渲染，行内标记按原文展示（库文本组件不接受 AnnotatedString）。 */
@Composable
private fun UpdateNotes(markdown: String) {
    val scheme = CouiKitTheme.colorScheme
    val blocks = remember(markdown) { MarkdownBlocks.parse(markdown) }
    if (blocks.isEmpty()) {
        Text(text = "该版本未提供更新说明。", kind = CouiTextKind.Description)
        return
    }
    blocks.forEach { block ->
        when (block) {
            is MarkdownBlock.Heading -> Text(
                text = block.text,
                kind = if (block.level <= 2) CouiTextKind.PreferenceTitle else CouiTextKind.Body,
            )
            is MarkdownBlock.Bullet -> NotesLine(prefix = "·", text = block.text)
            is MarkdownBlock.Ordered -> NotesLine(prefix = block.number.toString() + ".", text = block.text)
            is MarkdownBlock.Divider -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CouiPageDefaults.dividerHeight)
                    .background(scheme.outlineVariant),
            )
            is MarkdownBlock.Paragraph -> Text(text = block.text, kind = CouiTextKind.Body)
        }
    }
}

@Composable
private fun NotesLine(prefix: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.width(NotesPrefixMinWidth)) {
            Text(text = prefix, kind = CouiTextKind.Body)
        }
        Text(text = text, kind = CouiTextKind.Body)
    }
}

/** 更新设置卡：自动检查开关与忽略当前版本。 */
@Composable
private fun UpdateSettingsGroup(
    autoCheckUpdate: Boolean,
    ignoreSummary: String,
    ignoreEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onIgnoreToggle: () -> Unit,
) {
    Group(modifier = Modifier.padding(top = 8.dp), title = "更新设置") {
        Item(
            title = { Text(text = "自动检查更新", kind = CouiTextKind.PreferenceTitle) },
            summary = { Text(text = "每次启动应用时自动检查最新版本", kind = CouiTextKind.PreferenceSummary) },
            trailing = {
                Switch(checked = autoCheckUpdate, onCheckedChange = onAutoCheckChange)
            },
        )
        Divider()
        Item(
            title = { Text(text = "忽略此版本", kind = CouiTextKind.PreferenceTitle) },
            summary = { Text(text = ignoreSummary, kind = CouiTextKind.PreferenceSummary) },
            enabled = ignoreEnabled,
            onClick = if (ignoreEnabled) onIgnoreToggle else null,
        )
    }
}

/** 操作卡：手动检查、Releases 页、两侧版本号。 */
@Composable
private fun UpdateActionsGroup(
    currentVersion: String,
    release: ReleaseInfo?,
    checking: Boolean,
    onRecheck: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Group(modifier = Modifier.padding(top = 8.dp), title = "版本与操作") {
        Item(
            title = { Text(text = "手动检查更新", kind = CouiTextKind.PreferenceTitle) },
            summary = {
                Text(
                    text = if (checking) "正在检索远端版本信息…" else "点击从 GitHub Releases 检索最新版本",
                    kind = CouiTextKind.PreferenceSummary,
                )
            },
            enabled = !checking,
            onClick = if (checking) null else onRecheck,
        )
        Divider()
        Item(
            title = { Text(text = "GitHub Releases", kind = CouiTextKind.PreferenceTitle) },
            summary = { Text(text = "在浏览器查看完整发布说明", kind = CouiTextKind.PreferenceSummary) },
            onClick = onOpenRelease,
        )
        Divider()
        Item(
            title = { Text(text = "当前版本", kind = CouiTextKind.PreferenceTitle) },
            trailing = { Text(text = currentVersion, kind = CouiTextKind.PreferenceValue) },
        )
    }
}

/** 发现新版本时的确认弹窗：强确认放居中，行动按下载态收敛成一条主操作。 */
@Composable
private fun UpdateAvailableDialog(
    show: Boolean,
    currentVersion: String,
    release: ReleaseInfo?,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: (com.jm3.core.update.ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: (String) -> Unit,
    onIgnore: (String) -> Unit,
) {
    if (release == null) return
    val scheme = CouiKitTheme.colorScheme
    val asset = release.apk
    Dialog(
        onDismissRequest = onDismiss,
        visible = show,
        dark = CouiKitTheme.isDark,
        material = couiDialogMaterial(),
        placement = CouiDialogPlacement.Center,
        title = "发现新版本 " + release.version,
        message = "当前版本 " + currentVersion,
        confirmAction = when (downloadState) {
            is UpdateDownloadState.Done -> CouiDialogAction(
                text = "立即安装",
                onClick = { onInstall(downloadState.file) },
            )
            is UpdateDownloadState.Downloading -> CouiDialogAction(
                text = "取消下载",
                onClick = onCancelDownload,
            )
            else -> CouiDialogAction(
                text = if (asset == null) "前往 Releases 页面" else "立即下载更新",
                enabled = asset != null,
                onClick = {
                    if (asset == null) {
                        onOpenRelease(release.htmlUrl)
                    } else {
                        onDownload(asset)
                    }
                },
            )
        },
        dismissAction = CouiDialogAction(
            text = "忽略此版本",
            onClick = {
                // 下载失败时不再记忽略：用户还没拿到安装包，记下来会吞掉下一次提示
                if (downloadState !is UpdateDownloadState.Failed) onIgnore(release.version)
                onDismiss()
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (release.publishedDate.isNotBlank()) {
                Text(
                    text = "发布于 " + release.publishedDate,
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                )
            }
            when (downloadState) {
                is UpdateDownloadState.Idle -> Text(
                    text = asset?.let { "安装包 " + it.name + assetSizeSuffix(it.size) }
                        ?: "该版本未附带安装包，请前往 Releases 页面手动下载。",
                    kind = CouiTextKind.PreferenceSummary,
                    color = scheme.onSurfaceVariant,
                )
                is UpdateDownloadState.Downloading -> {
                    Text(
                        text = "正在下载 " + updateProgressText(downloadState, withSize = true),
                        kind = CouiTextKind.Body,
                    )
                    CouiProgressBar(progress = downloadState.progress)
                }
                is UpdateDownloadState.Done -> Text(
                    text = "安装包已下载完成，点击安装完成升级。",
                    kind = CouiTextKind.Body,
                )
                is UpdateDownloadState.Failed -> {
                    Text(text = downloadState.message, kind = CouiTextKind.Body, color = MiuixTheme.colorScheme.error)
                    CouiInlineButton(text = "重试", onClick = onResetDownload)
                }
            }
        }
    }
}

/** 站内动作按钮：库没有按钮组件，胶囊底 + 按压缩放即是本页全部按钮形态。 */
@Composable
private fun CouiInlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val scheme = CouiKitTheme.colorScheme
    val color = if (emphasised) scheme.onAccent else scheme.accent
    Box(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .background(if (emphasised) scheme.accent else scheme.accent.copy(alpha = 0.14f))
            .couiPressable(
                shape = CouiCapsuleShape,
                // 强调色实底按库的主按钮口径取 OpaqueMedium1，浅底胶囊用 ChipTab
                spotlight = if (emphasised) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, kind = CouiTextKind.PreferenceValue, color = color)
    }
}

/** 更新说明卡的标题：带版本号，让用户一眼看到看的是哪一版。 */
private fun changelogTitle(state: UpdateOutcome?): String = when (state) {
    is UpdateOutcome.Available -> "更新说明（" + state.release.version + "）"
    is UpdateOutcome.UpToDate -> "当前版本说明（" + state.release.version + "）"
    else -> "更新说明"
}

private fun assetSizeSuffix(size: Long): String =
    if (size > 0L) "（" + formatUpdateSize(size) + "）" else ""
