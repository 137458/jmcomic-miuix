// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBarButtonGroup
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.FoldableNotes
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.InlineCodeText
import com.jm3.app.miuix.ui.NotesFold
import com.jm3.app.miuix.ui.NotesPageViewport
import com.jm3.app.miuix.ui.UpdateAvailableDialog
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCircleActionButton
import com.jm3.app.miuix.ui.coui.component.CouiHeaderCapsuleAction
import com.jm3.app.miuix.ui.coui.component.CouiInstallProgressButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiContourSurface
import com.jm3.app.miuix.ui.coui.component.couiFloatingSurfaceEnabled
import com.jm3.app.miuix.ui.coui.component.couiSwitchRow
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.couiWithBottomPadding
import com.jm3.app.miuix.ui.coui.component.rememberCouiScreenScrollBehavior
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.copyTextToClipboard
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.formatUpdateSize
import com.jm3.app.miuix.ui.loadAppIcon
import com.jm3.app.miuix.ui.loadCurrentVersion
import com.jm3.app.miuix.ui.openUpdateReleasePage
import com.jm3.app.miuix.ui.parseInlineNotes
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.updateBrowserFallbackUrl
import com.jm3.app.miuix.ui.updateDialogDownloadDetail
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCenter
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdateOutcome
import com.jm3.core.update.UpdateProgress
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.UpdatePromptState
import com.jm3.core.update.VersionText
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 更新说明的块间距，与更新弹窗保持同一节奏。 */
private val NotesBlockSpacing = 6.dp

/** 说明项前缀列的最小宽度：让多位数序号与项目符号不挤在一起。 */
private val NotesPrefixMinWidth = 20.dp

/** 已是最新版本且更新说明较长时默认预览的块数（对标 OShin 折叠说明）。 */
private const val CHANGELOG_PREVIEW_BLOCK_LIMIT = 3

/** 底部固定主操作栏在实测前的默认内容避让高度（48dp 按钮 + 上下各 14dp）。 */
private val BottomActionDefaultClearance = 76.dp

private val HeroIconContainerShape = CouiShape(14.dp, 3f)
private val HeroIconContainerSize = 52.dp
private val HeroIconGlyphSize = 36.dp

/**
 * COUI 检查更新页：光感主卡 + 说明/设置/信息分组 + 底部填充进度操作栏。
 *
 * 检查逻辑原样复用 [UpdateCenter] 与 [UpdatePrompt]，新版本弹窗复用共享的 [UpdateAvailableDialog]；
 * 底部操作栏采用两遍裁剪反白胶囊进度按钮（对标 `COUIInstallLoadProgress`）并随下载态附带取消/重下圆键。
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
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showPreviewDialog by rememberSaveable { mutableStateOf(false) }
    var previewSimulating by remember { mutableStateOf(false) }
    var previewDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var autoCheckUpdate by remember { mutableStateOf(true) }
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
                promptedVersion = deps.promptedVersion,
            ),
        )
        state = outcome
        // 启动时被判定「该提示」才弹窗；被忽略的版本不再打扰，只在页面里留入口
        if (outcome is UpdateOutcome.Available && outcome.notify) {
            showPreviewDialog = false
            previewSimulating = false
            showDialog = true
            deps.promptedVersion = outcome.release.version
        }
    }

    // 预览弹窗里的模拟下载进度：便于直接验看进度条、取消下载与后台下载槽位，不发起真实网络请求
    LaunchedEffect(previewSimulating) {
        if (!previewSimulating) {
            previewDownloadState = UpdateDownloadState.Idle
            return@LaunchedEffect
        }
        val previewAsset = couiPreviewUpdateRelease(currentVersion, release).apk
        val total = (previewAsset?.size ?: 24L * 1024 * 1024).coerceAtLeast(1L)
        val steps = 15
        val perStepDelayMs = 180L
        for (step in 1..steps) {
            if (!isActive || !previewSimulating) return@LaunchedEffect
            val downloaded = (total * step) / steps
            val speed = (total / steps) * (1000L / perStepDelayMs)
            previewDownloadState = UpdateDownloadState.Downloading(
                downloaded = downloaded,
                total = total,
                speedBytesPerSec = speed,
            )
            delay(perStepDelayMs)
        }
        previewSimulating = false
        previewDownloadState = UpdateDownloadState.Idle
        showPreviewDialog = false
        snackbarHostState.showSnackbar("预览下载演示完成（未写入文件）")
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

    fun copyCurrentVersion() {
        copyTextToClipboard(
            context = context,
            scope = scope,
            snackbarHostState = snackbarHostState,
            label = "版本号",
            value = currentVersion,
            successMessage = "已复制版本号 v$currentVersion",
        )
    }

    fun install(file: File) {
        when (val result = deps.installUpdate(file)) {
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
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        val scrollBehavior = rememberCouiScreenScrollBehavior(canScroll = { scrollState.maxValue > 0 })
        var bottomBarBodyHeight by remember { mutableStateOf(BottomActionDefaultClearance) }
        val pageBackground = CouiKitTheme.colorScheme.background
        val floatingBackdrop = if (couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, hasBackdrop = true)) {
            rememberCouiBackdrop(drawSource = {
                drawRect(pageBackground)
                drawContent()
            })
        } else {
            null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentBackdrop = floatingBackdrop,
                topBar = {
                    LargeTopAppBar(
                        title = { CouiTopBarTitle(text = "检查更新", textAlign = TextAlign.Start) },
                        navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                        actions = {
                            TopAppBarButtonGroup {
                                CouiTopBarAction(
                                    icon = CouiIcons.Refresh,
                                    contentDescription = "重新检查",
                                    onClick = { if (state != null) attempt++ },
                                )
                            }
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
                        .couiVerticalScroll(scrollState)
                        .padding(
                            scrollBehavior.state.contentPadding(innerPadding, density)
                                .couiWithBottomPadding(
                                    couiPageExtraBottomDp(hasFab = false).dp + bottomBarBodyHeight,
                                ),
                        )
                        .consumeWindowInsets(innerPadding),
                ) {
                    UpdateHeroCard(
                        appName = appName,
                        currentVersion = currentVersion,
                        state = state,
                        downloadState = downloadState,
                        onOpenRelease = ::openRelease,
                    )
                    if (release != null) {
                        UpdateNotesGroup(
                            title = changelogTitle(state),
                            state = state,
                            release = release,
                            onOpenRelease = ::openRelease,
                        )
                    }
                    UpdateSettingsGroup(
                        autoCheckUpdate = autoCheckUpdate,
                        ignored = ignored,
                        ignoreSummary = ignoreSummary,
                        ignoreEnabled = hasNewVersion && latestVersion != null,
                        onAutoCheckChange = ::setAutoCheck,
                        onIgnoreToggle = { latestVersion?.let(::toggleIgnored) },
                    )
                    UpdateMetaGroup(
                        currentVersion = currentVersion,
                        state = state,
                        release = release,
                        onCopyVersion = ::copyCurrentVersion,
                        onOpenRelease = { openRelease(UpdateChecker.RELEASES_PAGE_URL) },
                    )
                    UpdateDebugGroup(
                        hasNewVersion = hasNewVersion,
                        onTestDialog = {
                            if (hasNewVersion) {
                                showPreviewDialog = false
                                previewSimulating = false
                                showDialog = true
                            } else {
                                previewSimulating = false
                                previewDownloadState = UpdateDownloadState.Idle
                                showPreviewDialog = true
                            }
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassSnackbarHost(
                    state = snackbarHostState,
                    modifier = Modifier.fillMaxWidth(),
                )
                UpdateBottomActionBar(
                    state = state,
                    downloadState = downloadState,
                    backdrop = floatingBackdrop,
                    onContentHeightMeasured = { px ->
                        bottomBarBodyHeight = with(density) { px.toDp() }
                    },
                    onRecheck = { attempt++ },
                    onOpenRelease = ::openRelease,
                    onStartDownload = { asset -> deps.updateDownloader.start(asset) },
                    onCancelDownload = { deps.updateDownloader.cancel() },
                    onResetDownload = { deps.updateDownloader.reset() },
                    onInstall = ::install,
                )
            }
        }

        val realRelease = (state as? UpdateOutcome.Available)?.release
        val usePreview = realRelease == null
        val previewRelease = remember(currentVersion, release) {
            couiPreviewUpdateRelease(currentVersion = currentVersion, remoteRelease = release)
        }
        val activeTarget = if (usePreview) {
            if (showPreviewDialog) previewRelease else null
        } else {
            realRelease
        }
        val dialogRelease = rememberRetainedTarget(activeTarget)
        val dialogVisible = if (usePreview) showPreviewDialog else showDialog
        if (dialogRelease != null) {
            UpdateAvailableDialog(
                show = dialogVisible,
                currentVersion = currentVersion,
                release = dialogRelease,
                downloadState = if (usePreview) previewDownloadState else downloadState,
                onDismiss = {
                    if (usePreview) {
                        showPreviewDialog = false
                        previewSimulating = false
                    } else {
                        showDialog = false
                    }
                },
                onDownload = {
                    if (usePreview) {
                        previewSimulating = true
                    } else {
                        dialogRelease.apk?.let { deps.updateDownloader.start(it) }
                    }
                },
                onOpenRelease = {
                    if (usePreview) {
                        showPreviewDialog = false
                        previewSimulating = false
                    } else {
                        showDialog = false
                    }
                    openRelease(dialogRelease.htmlUrl)
                },
                onIgnore = { version ->
                    if (usePreview) {
                        showPreviewDialog = false
                        previewSimulating = false
                        scope.launch { snackbarHostState.showSnackbar("预览模式：未写入忽略版本") }
                    } else {
                        showDialog = false
                        if (ignoredVersion != version) toggleIgnored(version)
                    }
                },
                onCancelDownload = {
                    if (usePreview) {
                        previewSimulating = false
                    } else {
                        deps.updateDownloader.cancel()
                    }
                },
            )
        }
    }
}

/**
 * 参考 ColorOS 软件更新页的顶部流体曲线玻璃视觉主卡：
 * 光感弧面玻璃底纹 + 应用图标 + Release 状态胶囊 + 大字态结论 + 软件版本对照 + 下载实时速率。
 */
@Composable
private fun UpdateHeroCard(
    appName: String,
    currentVersion: String,
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onOpenRelease: (String) -> Unit,
) {
    val context = LocalContext.current
    val dark = CouiKitTheme.isDark
    val scheme = CouiKitTheme.colorScheme
    val iconSizePx = with(LocalDensity.current) { HeroIconGlyphSize.roundToPx() }
    val appIcon = remember(context, iconSizePx) { loadAppIcon(context, iconSizePx) }
    val checking = state == null
    val hasFailed = state is UpdateOutcome.Failed
    val isAvailable = state is UpdateOutcome.Available

    val badgeIcon = when {
        checking -> CouiIcons.Refresh
        isAvailable -> CouiIcons.Download
        hasFailed -> CouiIcons.Info
        else -> CouiIcons.Check
    }
    val headlineText = couiUpdateHeadline(state)
    val versionLine = couiUpdateVersionLine(currentVersion, state)
    val subtitleText = when {
        state is UpdateOutcome.Available -> couiUpdateReleaseMetaLine(state.release)
        state is UpdateOutcome.UpToDate -> couiUpdateReleaseMetaLine(state.release)
            .ifBlank { "当前已安装公开发布的最新版本" }
        state is UpdateOutcome.Failed -> state.message
        else -> "正在连接 GitHub Releases 获取版本信息"
    }

    val spinAngle = if (checking) {
        val transition = rememberInfiniteTransition(label = "coui-update-check-spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
            ),
            label = "coui-update-check-angle",
        )
        angle
    } else {
        0f
    }

    val errorColor = CouiDialogDefaults.themeColors(dark).destructive
    val statusTint = if (hasFailed) errorColor else scheme.accent
    val heroBackdrop = if (couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, hasBackdrop = true)) {
        rememberCouiBackdrop(drawSource = {
            drawRect(scheme.card)
            drawContent()
        })
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing)
            .clip(CouiPageDefaults.shape),
    ) {
        UpdateHeroBackdrop(
            cardColor = scheme.card,
            statusTint = statusTint,
            dark = dark,
            backdrop = heroBackdrop,
            modifier = Modifier.matchParentSize(),
        )
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .couiContourSurface(
                    shape = CouiPageDefaults.shape,
                    surfaceColor = Color.Transparent,
                    cornerRadius = 24.dp,
                    cornerWeight = 3f,
                    style = CouiContourStyle.Dialog,
                    dark = dark,
                    emphasized = isAvailable || hasFailed,
                    fallbackBorderColor = scheme.outlineVariant.copy(alpha = if (dark) 0.36f else 0.22f),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (appIcon != null) {
                Box(
                    modifier = Modifier
                        .size(HeroIconContainerSize)
                        .clip(HeroIconContainerShape)
                        .background(scheme.onSurface.copy(alpha = if (dark) 0.08f else 0.04f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = appName,
                        modifier = Modifier.size(HeroIconGlyphSize),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .clip(CouiCapsuleShape)
                    .background(statusTint.copy(alpha = if (dark) 0.18f else 0.11f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer { rotationZ = spinAngle },
                    tint = statusTint,
                )
                Text(
                    text = "$appName Release",
                    kind = CouiTextKind.GroupFooter,
                    color = statusTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            BasicText(
                text = headlineText,
                style = rememberCouiTextStyle(CouiTextKind.HomeTitle).copy(
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(CouiCapsuleShape)
                    .background(scheme.onSurface.copy(alpha = if (dark) 0.08f else 0.05f))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text = versionLine,
                    kind = CouiTextKind.ItemValue,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitleText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitleText,
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val apkAsset = if (isAvailable) state.release.apk else null
            if (apkAsset != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = apkAsset.name,
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.84f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isAvailable && downloadState is UpdateDownloadState.Downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CouiCapsuleShape)
                        .background(scheme.accent.copy(alpha = if (dark) 0.16f else 0.10f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = updateDialogDownloadDetail(downloadState),
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (isAvailable && downloadState is UpdateDownloadState.Failed) {
                Spacer(modifier = Modifier.height(12.dp))
                CouiBodyText(
                    text = downloadState.message,
                    color = errorColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                CouiHeaderCapsuleAction(
                    text = "用浏览器下载",
                    trailingIcon = CouiIcons.Forward,
                    tinted = true,
                    backdrop = heroBackdrop,
                    onClick = { onOpenRelease(updateBrowserFallbackUrl(state.release)) },
                )
            } else if (hasFailed) {
                Spacer(modifier = Modifier.height(12.dp))
                CouiHeaderCapsuleAction(
                    text = "在浏览器打开 Releases 页",
                    trailingIcon = CouiIcons.Forward,
                    tinted = true,
                    backdrop = heroBackdrop,
                    onClick = { onOpenRelease(UpdateChecker.RELEASES_PAGE_URL) },
                )
            }
        }
    }
}

/** COUI 顶部状态卡底纹：基于主题卡片底色叠加柔和的状态色径向微光，并为前景胶囊按钮提供同源采样背景。 */
@Composable
private fun UpdateHeroBackdrop(
    cardColor: Color,
    statusTint: Color,
    dark: Boolean,
    backdrop: CouiBackdrop?,
    modifier: Modifier = Modifier,
) {
    val backdropModifier = if (backdrop != null) Modifier.couiBackdrop(backdrop) else Modifier
    Box(
        modifier = modifier
            .then(backdropModifier)
            .background(cardColor)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            statusTint.copy(alpha = if (dark) 0.15f else 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = maxOf(size.width, size.height) * 0.85f,
                    ),
                )
            },
    )
}

/**
 * 底部悬浮主操作栏：API 34+ 通过 [backdrop] 为 [CouiInstallProgressButton] 与 [CouiCircleActionButton]
 * 提供原厂 COUI 凝光玻璃材质与折射采样，滚过底栏的内容透过玻璃自然流动。
 */
@Composable
private fun UpdateBottomActionBar(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    backdrop: CouiBackdrop?,
    onContentHeightMeasured: (Int) -> Unit,
    onRecheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onStartDownload: (ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val mode = couiUpdateActionBarMode(state, downloadState)
    val label = couiUpdateButtonLabel(state, downloadState)
    val progressFraction = couiUpdateProgressFraction(downloadState)
    val isDownloading = mode == CouiUpdateActionBarMode.DOWNLOADING
    val enabled = mode != CouiUpdateActionBarMode.CHECKING && !isDownloading
    val hasFloatingSurface = couiFloatingSurfaceEnabled(Build.VERSION.SDK_INT, backdrop != null)

    val containerModifier = if (hasFloatingSurface) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.background.copy(alpha = 0f),
                        scheme.background.copy(alpha = 0.94f),
                        scheme.background,
                    ),
                ),
            )
    }

    Box(
        modifier = containerModifier
            .navigationBarsPadding()
            .onSizeChanged { onContentHeightMeasured(it.height) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .readableWidth()
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CouiInstallProgressButton(
                text = label,
                enabled = enabled,
                progress = progressFraction,
                isProgressActive = isDownloading,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                onClick = {
                    when (mode) {
                        CouiUpdateActionBarMode.READY_TO_INSTALL -> {
                            (downloadState as? UpdateDownloadState.Done)?.let { onInstall(it.file) }
                        }
                        CouiUpdateActionBarMode.DOWNLOAD_FAILED -> {
                            val asset = (state as? UpdateOutcome.Available)?.release?.apk
                            if (asset != null) onStartDownload(asset) else onResetDownload()
                        }
                        CouiUpdateActionBarMode.AVAILABLE_IDLE -> {
                            val release = (state as? UpdateOutcome.Available)?.release ?: return@CouiInstallProgressButton
                            val asset = release.apk
                            if (asset != null) onStartDownload(asset) else onOpenRelease(release.htmlUrl)
                        }
                        CouiUpdateActionBarMode.CHECK_FAILED,
                        CouiUpdateActionBarMode.UP_TO_DATE,
                        -> onRecheck()
                        CouiUpdateActionBarMode.CHECKING,
                        CouiUpdateActionBarMode.DOWNLOADING,
                        -> Unit
                    }
                },
            )
            if (isDownloading) {
                CouiCircleActionButton(
                    icon = CouiIcons.Close,
                    contentDescription = "取消下载",
                    backdrop = backdrop,
                    onClick = onCancelDownload,
                )
            } else if (mode == CouiUpdateActionBarMode.READY_TO_INSTALL) {
                CouiCircleActionButton(
                    icon = CouiIcons.Refresh,
                    contentDescription = "重新下载",
                    backdrop = backdrop,
                    onClick = onResetDownload,
                )
            }
        }
    }
}

/** 更新日志卡：标题栏提供「发布详情」胶囊，长说明底部通过 COUI 原生分割线 + [Item] 行展开收起。 */
@Composable
private fun UpdateNotesGroup(
    title: String,
    state: UpdateOutcome?,
    release: ReleaseInfo,
    onOpenRelease: (String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val blocks = remember(release.notes) { MarkdownBlocks.parse(release.notes) }
    val collapsible = couiChangelogCollapsible(blocks.size)
    var userToggled by rememberSaveable(release.tagName) { mutableStateOf(false) }
    var expanded by rememberSaveable(release.tagName) {
        mutableStateOf(couiChangelogDefaultExpanded(state, blocks.size))
    }
    LaunchedEffect(state, release.tagName) {
        if (!userToggled) {
            expanded = couiChangelogDefaultExpanded(state, blocks.size)
        }
    }
    val previewLimit = couiChangelogPreviewLimit(blocks.size, expanded)
    val hiddenCount = couiChangelogCollapsedHiddenCount(blocks.size)

    Group(
        title = title,
        headerActions = {
            if (release.htmlUrl.isNotBlank()) {
                CouiHeaderCapsuleAction(
                    text = "发布详情",
                    trailingIcon = CouiIcons.Forward,
                    tinted = false,
                    onClick = { onOpenRelease(release.htmlUrl) },
                )
            }
        },
    ) {
        // 说明正文统一走 FoldableNotes：展开后在有界视口内滚动，折叠行留在视口外侧，
        // 无论说明有多少屏，"收起更新说明"始终贴在卡片下缘。
        FoldableNotes(
            fold = if (collapsible) {
                NotesFold(
                    expanded = expanded,
                    label = if (expanded) "收起更新说明" else "展开全部更新说明",
                    onToggle = {
                        userToggled = true
                        expanded = !expanded
                    },
                )
            } else {
                null
            },
            spec = NotesPageViewport,
            modifier = Modifier.animateContentSize(),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = CouiPageDefaults.rowPadding,
                            vertical = CouiPageDefaults.contentPadding + 2.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(NotesBlockSpacing),
                ) {
                    Text(
                        text = release.name.ifBlank { "版本特性说明" },
                        kind = CouiTextKind.ItemTitle,
                    )
                    if (previewLimit != 0) {
                        UpdateNotes(
                            markdown = release.notes,
                            previewLimit = previewLimit,
                        )
                    }
                }
            },
            footer = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider()
                    Item(
                        title = {
                            Text(
                                text = if (expanded) "收起更新说明" else "展开全部更新说明",
                                kind = CouiTextKind.ItemValue,
                                color = scheme.accent,
                            )
                        },
                        summary = if (!expanded) {
                            {
                                Text(
                                    text = if (previewLimit == 0) {
                                        "共 ${blocks.size} 段内容已收起"
                                    } else {
                                        "还有 $hiddenCount 段内容未展开"
                                    },
                                    kind = CouiTextKind.GroupFooter,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            null
                        },
                        trailing = {
                            Icon(
                                imageVector = if (expanded) CouiIcons.ChevronUp else CouiIcons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = scheme.accent,
                            )
                        },
                        onClick = {
                            userToggled = true
                            expanded = !expanded
                        },
                    )
                }
            },
        )
    }
}

/** 更新说明正文：按块与行内 Markdown 解析，超链接可点击打开、行内代码以圆角胶囊渲染。 */
@Composable
private fun UpdateNotes(
    markdown: String,
    previewLimit: Int? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    val blocks = remember(markdown) { MarkdownBlocks.parse(markdown) }
    if (blocks.isEmpty()) {
        Text(text = "该版本未提供更新说明。", kind = CouiTextKind.GroupFooter)
        return
    }
    val visibleBlocks = if (previewLimit != null) blocks.take(previewLimit) else blocks
    val bodyStyle = rememberCouiBodyTextStyle()
    visibleBlocks.forEach { block ->
        when (block) {
            is MarkdownBlock.Heading -> {
                val styled = rememberCouiNotesInline(block.text)
                val kind = if (block.level <= 2) CouiTextKind.ItemTitle else CouiTextKind.Search
                CouiNotesInlineText(
                    text = styled,
                    style = rememberCouiTextStyle(kind),
                )
            }
            is MarkdownBlock.Bullet -> NotesLine(prefix = "·", text = block.text)
            is MarkdownBlock.Ordered -> NotesLine(prefix = block.number.toString() + ".", text = block.text)
            is MarkdownBlock.Divider -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CouiPageDefaults.dividerHeight)
                    .background(scheme.outlineVariant),
            )
            is MarkdownBlock.Paragraph -> CouiNotesInlineText(
                text = rememberCouiNotesInline(block.text),
                style = bodyStyle,
            )
        }
    }
}

@Composable
private fun NotesLine(prefix: String, text: String) {
    val scheme = CouiKitTheme.colorScheme
    val styled = rememberCouiNotesInline(text)
    val bodyStyle = rememberCouiBodyTextStyle()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.width(NotesPrefixMinWidth)) {
            CouiBodyText(text = prefix, color = scheme.onSurfaceVariant)
        }
        CouiNotesInlineText(
            text = styled,
            style = bodyStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CouiNotesInlineText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val dark = CouiKitTheme.isDark
    val scheme = CouiKitTheme.colorScheme
    InlineCodeText(
        text = text,
        style = style,
        color = scheme.onSurface,
        codeColor = scheme.onSurface.copy(alpha = 0.90f),
        pillBackground = scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.065f),
        pillBorder = scheme.outlineVariant.copy(alpha = if (dark) 0.50f else 0.42f),
        modifier = modifier,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun rememberCouiNotesInline(text: String): AnnotatedString {
    val scheme = CouiKitTheme.colorScheme
    val dark = CouiKitTheme.isDark
    val codeBackground = scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.08f)
    return remember(text, scheme.accent, codeBackground) {
        couiInlineNotes(
            text = text,
            accentColor = scheme.accent,
            codeBackground = codeBackground,
        )
    }
}

/** 更新设置卡：整行可点的自动检查与忽略版本开关。 */
@Composable
private fun UpdateSettingsGroup(
    autoCheckUpdate: Boolean,
    ignored: Boolean,
    ignoreSummary: String,
    ignoreEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onIgnoreToggle: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group(title = "更新设置") {
        Item(
            modifier = Modifier.couiSwitchRow(
                value = autoCheckUpdate,
                onValueChange = onAutoCheckChange,
            ),
            title = { Text(text = "自动检查更新", kind = CouiTextKind.ItemTitle) },
            summary = {
                Text(
                    text = "每次启动应用时自动检查最新版本",
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            },
            trailing = {
                Switch(checked = autoCheckUpdate, onCheckedChange = null)
            },
        )
        Divider()
        Item(
            modifier = Modifier.couiSwitchRow(
                value = ignored,
                enabled = ignoreEnabled,
                onValueChange = { onIgnoreToggle() },
            ),
            enabled = ignoreEnabled,
            title = {
                Text(
                    text = "忽略此版本",
                    kind = CouiTextKind.ItemTitle,
                    color = if (ignoreEnabled) scheme.onSurface else scheme.onSurfaceVariant,
                )
            },
            summary = {
                Text(
                    text = ignoreSummary,
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            },
            trailing = {
                Switch(
                    checked = ignored,
                    onCheckedChange = null,
                    enabled = ignoreEnabled,
                )
            },
        )
    }
}

/** 版本信息与外部仓库入口。 */
@Composable
private fun UpdateMetaGroup(
    currentVersion: String,
    state: UpdateOutcome?,
    release: ReleaseInfo?,
    onCopyVersion: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val remoteVersionText = when {
        state == null -> "检测中…"
        release != null -> release.version
        state is UpdateOutcome.Failed -> "获取失败"
        else -> "—"
    }
    Group(title = "版本信息") {
        Item(
            title = { Text(text = "当前版本", kind = CouiTextKind.ItemTitle) },
            summary = {
                Text(
                    text = "点击复制版本号",
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            },
            trailing = {
                Text(
                    text = currentVersion,
                    kind = CouiTextKind.ItemValue,
                    color = scheme.onSurfaceVariant,
                )
            },
            onClick = onCopyVersion,
        )
        Divider()
        Item(
            title = { Text(text = "远端最新版本", kind = CouiTextKind.ItemTitle) },
            trailing = {
                Text(
                    text = remoteVersionText,
                    kind = CouiTextKind.ItemValue,
                    color = scheme.onSurfaceVariant,
                )
            },
        )
        Divider()
        Item(
            title = { Text(text = "GitHub Releases", kind = CouiTextKind.ItemTitle) },
            summary = {
                Text(
                    text = "在浏览器查看完整发布说明",
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant,
                )
            },
            onClick = onOpenRelease,
        )
    }
}

/** 调试预览组：提供一键验证更新弹窗、折叠日志与模拟下载进度的入口。 */
@Composable
private fun UpdateDebugGroup(
    hasNewVersion: Boolean,
    onTestDialog: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group(title = "调试工具") {
        Item(
            title = { Text(text = "测试更新弹窗", kind = CouiTextKind.ItemTitle) },
            summary = {
                Text(
                    text = if (hasNewVersion) {
                        "重新弹出当前新版本更新弹窗"
                    } else {
                        "使用示例版本与发布说明预览更新弹窗"
                    },
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant,
                )
            },
            onClick = onTestDialog,
        )
    }
}

/** 更新说明卡的标题：带版本号，让用户一眼看到看的是哪一版；区分本地领先与常规最新。 */
private fun changelogTitle(state: UpdateOutcome?): String = couiUpdateChangelogTitle(state)

internal enum class CouiUpdateActionBarMode {
    CHECKING,
    DOWNLOADING,
    READY_TO_INSTALL,
    DOWNLOAD_FAILED,
    AVAILABLE_IDLE,
    CHECK_FAILED,
    UP_TO_DATE,
}

internal fun couiUpdateHeadline(state: UpdateOutcome?): String = when (state) {
    null -> "正在检测更新"
    is UpdateOutcome.Available -> "发现新版本"
    is UpdateOutcome.Failed -> "检查更新失败"
    is UpdateOutcome.UpToDate -> if (state.localAhead) "当前版本已领先" else "已是最新版本"
}

internal fun couiUpdateVersionLine(currentVersion: String, state: UpdateOutcome?): String = when (state) {
    is UpdateOutcome.Available -> "软件版本  v$currentVersion → v${state.release.version}"
    is UpdateOutcome.UpToDate -> if (state.localAhead) {
        "本地 v$currentVersion · 远端 v${state.release.version}"
    } else {
        "软件版本  v$currentVersion"
    }
    else -> "软件版本  v$currentVersion"
}

internal fun couiUpdateChangelogTitle(state: UpdateOutcome?): String = when (state) {
    is UpdateOutcome.Available -> "更新说明（${state.release.version}）"
    is UpdateOutcome.UpToDate -> if (state.localAhead) {
        "最新公开发布说明（${state.release.version}）"
    } else {
        "当前版本说明（${state.release.version}）"
    }
    else -> "更新说明"
}

internal fun couiUpdateProgressFraction(downloadState: UpdateDownloadState): Float? = when (downloadState) {
    is UpdateDownloadState.Downloading ->
        if (downloadState.total > 0L) UpdateProgress.fraction(downloadState.downloaded, downloadState.total) else null
    is UpdateDownloadState.Done -> 1f
    else -> 0f
}

internal fun couiUpdateActionBarMode(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
): CouiUpdateActionBarMode = when {
    state == null -> CouiUpdateActionBarMode.CHECKING
    downloadState is UpdateDownloadState.Downloading -> CouiUpdateActionBarMode.DOWNLOADING
    downloadState is UpdateDownloadState.Done -> CouiUpdateActionBarMode.READY_TO_INSTALL
    downloadState is UpdateDownloadState.Failed -> CouiUpdateActionBarMode.DOWNLOAD_FAILED
    state is UpdateOutcome.Available -> CouiUpdateActionBarMode.AVAILABLE_IDLE
    state is UpdateOutcome.Failed -> CouiUpdateActionBarMode.CHECK_FAILED
    else -> CouiUpdateActionBarMode.UP_TO_DATE
}

internal fun couiInlineNotes(
    text: String,
    accentColor: Color,
    codeBackground: Color,
): AnnotatedString = parseInlineNotes(
    text = text,
    codeColor = accentColor,
    linkColor = accentColor,
    codeBackground = codeBackground,
)

internal fun couiChangelogDefaultExpanded(
    state: UpdateOutcome?,
    blockCount: Int,
): Boolean = when (state) {
    is UpdateOutcome.Available -> true
    is UpdateOutcome.UpToDate -> blockCount <= CHANGELOG_PREVIEW_BLOCK_LIMIT
    else -> false
}

internal fun couiChangelogCollapsible(blockCount: Int): Boolean =
    blockCount > 0

internal fun couiChangelogPreviewLimit(
    blockCount: Int,
    expanded: Boolean,
): Int? = when {
    expanded -> null
    blockCount > CHANGELOG_PREVIEW_BLOCK_LIMIT -> 2
    else -> 0
}

internal fun couiChangelogCollapsedHiddenCount(blockCount: Int): Int =
    (blockCount - (couiChangelogPreviewLimit(blockCount, expanded = false) ?: blockCount)).coerceAtLeast(0)

internal fun couiUpdateButtonLabel(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
): String = when {
    state == null -> "正在检测更新…"
    downloadState is UpdateDownloadState.Downloading -> {
        if (downloadState.total > 0L) {
            val pct = UpdateProgress.percent(downloadState.progress)
            "正在下载… $pct%"
        } else {
            "正在下载… ${formatUpdateSize(downloadState.downloaded)}"
        }
    }
    downloadState is UpdateDownloadState.Done -> "下载完成，立即安装"
    downloadState is UpdateDownloadState.Failed -> "重试下载"
    state is UpdateOutcome.Available -> if (state.release.apk != null) "下载并安装" else "前往 Releases 下载"
    state is UpdateOutcome.Failed -> "重新检查更新"
    else -> "检查更新"
}

internal fun couiUpdateReleaseMetaLine(release: ReleaseInfo): String = buildList {
    if (release.publishedDate.isNotBlank()) add("发布于 " + release.publishedDate)
    release.apk?.takeIf { it.size > 0L }?.let { add(formatUpdateSize(it.size)) }
}.joinToString(" | ")

internal fun couiNextPreviewVersion(currentVersion: String): String {
    val normalized = VersionText.normalized(currentVersion)
    if (normalized.isBlank()) return "1.0.1"
    val core = normalized.substringBefore('-').trim()
    val parts = core.split('.').mapNotNull { it.toIntOrNull() }
    return when {
        parts.size >= 3 -> "${parts[0]}.${parts[1]}.${parts[2] + 1}"
        parts.size == 2 -> "${parts[0]}.${parts[1]}.1"
        parts.size == 1 -> "${parts[0]}.0.1"
        else -> "$normalized-preview"
    }
}

internal fun couiPreviewUpdateRelease(
    currentVersion: String,
    remoteRelease: ReleaseInfo?,
): ReleaseInfo {
    val previewVersion = couiNextPreviewVersion(currentVersion)
    val fallbackAsset = ReleaseAsset(
        name = "jm3-v$previewVersion-universal.apk",
        size = 24L * 1024 * 1024,
        downloadUrl = UpdateChecker.RELEASES_PAGE_URL,
    )
    val sampleNotes = """
        ### 界面与交互优化
        - 检查更新主按钮接入 **COUI 凝光玻璃** 悬浮材质与跟手光效
        - 新增 `CouiInstallProgressButton` 双遍裁剪反白进度与不定态流光
        - 优化更新日志展开/收起行与弹窗内 `忽略此版本` 交互质感

        ### 修复与稳定性
        - 修复总长度未知时下载进度显示 `0%` 的问题
        - 完整发布记录请见 [GitHub Releases](${UpdateChecker.RELEASES_PAGE_URL})
    """.trimIndent()
    val baseNotes = remoteRelease?.notes?.trim().orEmpty()
    val previewNotes = when {
        baseNotes.lines().count { it.isNotBlank() } >= 6 -> baseNotes
        baseNotes.isNotBlank() -> "$baseNotes\n\n$sampleNotes"
        else -> sampleNotes
    }
    return ReleaseInfo(
        tagName = "v$previewVersion",
        name = "预览版本 v$previewVersion",
        notes = previewNotes,
        htmlUrl = remoteRelease?.htmlUrl?.takeIf { it.isNotBlank() } ?: UpdateChecker.RELEASES_PAGE_URL,
        publishedAt = remoteRelease?.publishedAt?.takeIf { it.isNotBlank() } ?: "2026-11-12T12:00:00Z",
        assets = remoteRelease?.assets?.takeIf { it.any { asset -> asset.name.endsWith(".apk", ignoreCase = true) } }
            ?: listOf(fallbackAsset),
    )
}
