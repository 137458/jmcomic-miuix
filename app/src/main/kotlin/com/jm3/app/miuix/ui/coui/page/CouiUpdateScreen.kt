// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCustomStyledText
import com.jm3.app.miuix.ui.coui.component.CouiDetailButtonShape
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.couiWithBottomPadding
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.formatUpdateSize
import com.jm3.app.miuix.ui.formatUpdateSpeed
import com.jm3.app.miuix.ui.loadCurrentVersion
import com.jm3.app.miuix.ui.openUpdateReleasePage
import com.jm3.app.miuix.ui.parseInlineNotes
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.updateBrowserFallbackUrl
import com.jm3.app.miuix.ui.updateProgressText
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import top.yukonga.miuix.kmp.basic.Icon
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

/** 已是最新版本且更新说明较长时默认预览的块数（对标 OShin 折叠说明）。 */
private const val CHANGELOG_PREVIEW_BLOCK_LIMIT = 3

/** 底部固定主操作栏的额外内容避让高度，确保滚动最底部卡片完整露出来。 */
private val BottomActionClearance = 84.dp

/** 底部主操作进度动画时长（参考 OShin 软件更新页 250ms 填充节奏）。 */
private const val DOWNLOAD_PROGRESS_ANIM_MS = 250

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
    var showDialog by rememberSaveable { mutableStateOf(false) }
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
                        .padding(
                            scrollBehavior.state.contentPadding(innerPadding, LocalDensity.current)
                                .couiWithBottomPadding(
                                    couiPageExtraBottomDp(hasFab = false).dp + BottomActionClearance,
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
                        onCancelDownload = { deps.updateDownloader.cancel() },
                    )
                    if (release != null) {
                        UpdateNotesGroup(
                            title = changelogTitle(state),
                            state = state,
                            release = release,
                        )
                    }
                    UpdateSettingsGroup(
                        autoCheckUpdate = autoCheckUpdate,
                        ignoreSummary = ignoreSummary,
                        ignoreEnabled = hasNewVersion && latestVersion != null,
                        onAutoCheckChange = ::setAutoCheck,
                        onIgnoreToggle = { latestVersion?.let(::toggleIgnored) },
                    )
                    UpdateMetaGroup(
                        currentVersion = currentVersion,
                        release = release,
                        onOpenRelease = { openRelease(UpdateChecker.RELEASES_PAGE_URL) },
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
                    onRecheck = { attempt++ },
                    onOpenRelease = ::openRelease,
                    onStartDownload = { asset -> deps.updateDownloader.start(asset) },
                    onResetDownload = { deps.updateDownloader.reset() },
                    onInstall = ::install,
                )
            }
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

/**
 * 参考 OShin 软件更新页的顶部流体曲线玻璃视觉主卡：
 * 光感弧面玻璃底纹 + Release 标识胶囊 + 大字态结论 + 软件版本对照 + 发布元信息。
 */
@Composable
private fun UpdateHeroCard(
    appName: String,
    currentVersion: String,
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onOpenRelease: (String) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val dark = CouiKitTheme.isDark
    val scheme = CouiKitTheme.colorScheme
    val release = when (state) {
        is UpdateOutcome.Available -> state.release
        is UpdateOutcome.UpToDate -> state.release
        else -> null
    }
    val checking = state == null
    val hasFailed = state is UpdateOutcome.Failed
    val isAvailable = state is UpdateOutcome.Available

    val badgeIcon = when {
        checking -> CouiIcons.Refresh
        isAvailable -> CouiIcons.Download
        hasFailed -> CouiIcons.Info
        else -> CouiIcons.Check
    }
    val headlineText = when {
        checking -> "正在检测更新"
        isAvailable -> "发现新版本"
        hasFailed -> "检查更新失败"
        else -> "已是最新版本"
    }
    val versionLine = when {
        isAvailable && release != null -> "软件版本  v$currentVersion → v${release.version}"
        else -> "软件版本  v$currentVersion"
    }
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

    val borderBrush = Brush.verticalGradient(
        colors = if (dark) {
            listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f))
        } else {
            listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.18f))
        },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing)
            .clip(CouiPageDefaults.shape)
            .border(1.dp, borderBrush, CouiPageDefaults.shape),
    ) {
        UpdateHeroBackdrop(
            dark = dark,
            accentTint = if (hasFailed) MiuixTheme.colorScheme.error else scheme.accent,
            hasFailed = hasFailed,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .clip(CouiCapsuleShape)
                    .background(Color.White.copy(alpha = if (dark) 0.14f else 0.20f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.34f), CouiCapsuleShape)
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
                    tint = Color.White,
                )
                Text(
                    text = "$appName Release",
                    kind = CouiTextKind.GroupFooter,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            BasicText(
                text = headlineText,
                style = rememberCouiTextStyle(CouiTextKind.HomeTitle).copy(
                    color = Color.White,
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
                    .background(Color.White.copy(alpha = if (dark) 0.15f else 0.22f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.38f), CouiCapsuleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text = versionLine,
                    kind = CouiTextKind.ItemValue,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitleText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitleText,
                    kind = CouiTextKind.ItemSummary,
                    color = Color.White.copy(alpha = 0.88f),
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
                    color = Color.White.copy(alpha = 0.74f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isAvailable && downloadState is UpdateDownloadState.Downloading) {
                Spacer(modifier = Modifier.height(14.dp))
                CouiBodyText(
                    text = updateProgressText(downloadState, withSize = true),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (downloadState.speedBytesPerSec > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "下载速度 " + formatUpdateSpeed(downloadState.speedBytesPerSec),
                        kind = CouiTextKind.GroupFooter,
                        color = Color.White.copy(alpha = 0.80f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                HeroFrostedLinkButton(text = "取消下载", onClick = onCancelDownload)
            } else if (isAvailable && downloadState is UpdateDownloadState.Failed) {
                Spacer(modifier = Modifier.height(12.dp))
                CouiBodyText(
                    text = downloadState.message,
                    color = Color(0xFFFFD8D6),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                HeroFrostedLinkButton(
                    text = "用浏览器下载",
                    onClick = { onOpenRelease(updateBrowserFallbackUrl(state.release)) },
                )
            } else if (hasFailed) {
                Spacer(modifier = Modifier.height(12.dp))
                HeroFrostedLinkButton(
                    text = "在浏览器打开 Releases 页",
                    onClick = { onOpenRelease(UpdateChecker.RELEASES_PAGE_URL) },
                )
            }
        }
    }
}

/** 流体曲线光感玻璃底纹：以代码矢量层模拟 ColorOS / OShin 更新顶图的暖橙向钴蓝折射弧面。 */
@Composable
private fun UpdateHeroBackdrop(
    dark: Boolean,
    accentTint: Color,
    hasFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseStart = when {
            hasFailed && dark -> Color(0xFF4A1816)
            hasFailed -> Color(0xFFB3382C)
            dark -> Color(0xFF3E1822)
            else -> Color(0xFFD63B24)
        }
        val baseEnd = when {
            hasFailed && dark -> Color(0xFF1A2236)
            hasFailed -> Color(0xFF5D2D4E)
            dark -> Color(0xFF0E295E)
            else -> Color(0xFF1F6FEB)
        }
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(baseStart, baseEnd),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        // 左侧暖橙光晕与右侧冷蓝光晕交叠
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (dark) Color(0x99DF5A2B) else Color(0xCCFF7A3D),
                    Color.Transparent,
                ),
                center = Offset(w * 0.14f, h * 0.65f),
                radius = w * 0.62f,
            ),
            radius = w * 0.62f,
            center = Offset(w * 0.14f, h * 0.65f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentTint.copy(alpha = if (dark) 0.55f else 0.72f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.88f, h * 0.35f),
                radius = w * 0.65f,
            ),
            radius = w * 0.65f,
            center = Offset(w * 0.88f, h * 0.35f),
        )
        // 叠层透镜弧面及高光描边
        val arc1Center = Offset(w * 0.72f, h * 0.18f)
        val arc1Radius = maxOf(w, h) * 0.56f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.10f else 0.22f),
                    Color.Transparent,
                ),
                center = arc1Center,
                radius = arc1Radius,
            ),
            radius = arc1Radius,
            center = arc1Center,
        )
        drawCircle(
            color = Color.White.copy(alpha = if (dark) 0.22f else 0.42f),
            radius = arc1Radius,
            center = arc1Center,
            style = Stroke(width = 1.2.dp.toPx()),
        )
        val arc2Center = Offset(w * 0.68f, h * 1.05f)
        val arc2Radius = maxOf(w, h) * 0.52f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.08f else 0.18f),
                    Color.Transparent,
                ),
                center = arc2Center,
                radius = arc2Radius,
            ),
            radius = arc2Radius,
            center = arc2Center,
        )
        drawCircle(
            color = Color.White.copy(alpha = if (dark) 0.16f else 0.32f),
            radius = arc2Radius,
            center = arc2Center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun HeroFrostedLinkButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = Color.White.copy(alpha = 0.18f),
                spotlight = SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.36f), CouiCapsuleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemValue,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 底部固定主操作栏：对齐 OShin 的居中大边距（40dp 左右留白）与 250ms 填充下载进度。
 */
@Composable
private fun UpdateBottomActionBar(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onRecheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onStartDownload: (com.jm3.core.update.ReleaseAsset) -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
) {
    val dark = CouiKitTheme.isDark
    val scheme = CouiKitTheme.colorScheme
    val checking = state == null
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    val targetProgress = if (downloadState is UpdateDownloadState.Downloading) {
        downloadState.progress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = DOWNLOAD_PROGRESS_ANIM_MS),
        label = "coui-update-bottom-progress",
    )
    val label = couiUpdateButtonLabel(state, downloadState)
    val enabled = !checking && !isDownloading

    val trackBg = when {
        checking -> scheme.onSurface.copy(alpha = if (dark) 0.10f else 0.08f)
        isDownloading -> scheme.accent.copy(alpha = if (dark) 0.22f else 0.16f)
        else -> scheme.accent
    }
    val labelColor = when {
        checking -> scheme.onSurfaceVariant
        isDownloading -> scheme.onSurface
        else -> scheme.onAccent
    }

    Box(
        modifier = Modifier
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
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .readableWidth()
                .fillMaxWidth()
                .height(48.dp)
                .couiPressable(
                    shape = CouiCapsuleShape,
                    backgroundColor = trackBg,
                    spotlight = if (enabled) SpotlightPreset.OpaqueMedium1 else null,
                    enabled = enabled,
                    scaleOnPress = enabled,
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = {
                        when {
                            downloadState is UpdateDownloadState.Done -> onInstall(downloadState.file)
                            downloadState is UpdateDownloadState.Failed -> onResetDownload()
                            state is UpdateOutcome.Available -> {
                                val asset = state.release.apk
                                if (asset != null) onStartDownload(asset) else onOpenRelease(state.release.htmlUrl)
                            }
                            else -> onRecheck()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isDownloading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CouiCapsuleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(scheme.accent),
                    )
                }
            }
            Text(
                text = label,
                kind = CouiTextKind.ItemTitle,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 更新日志卡：已获取发布信息后专卡呈现可交互超链接与折叠切换（OShin 同款收起/展开）。 */
@Composable
private fun UpdateNotesGroup(
    title: String,
    state: UpdateOutcome?,
    release: ReleaseInfo,
) {
    val scheme = CouiKitTheme.colorScheme
    val blocks = remember(release.notes) { MarkdownBlocks.parse(release.notes) }
    val collapsible = blocks.size > CHANGELOG_PREVIEW_BLOCK_LIMIT
    var expanded by rememberSaveable(release.tagName) {
        mutableStateOf(couiChangelogDefaultExpanded(state, blocks.size))
    }
    LaunchedEffect(state, release.tagName) {
        expanded = couiChangelogDefaultExpanded(state, blocks.size)
    }

    Group(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
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
            UpdateNotes(
                markdown = release.notes,
                previewLimit = if (collapsible && !expanded) CHANGELOG_PREVIEW_BLOCK_LIMIT else null,
            )
            if (collapsible) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .couiPressable(
                            shape = CouiDetailButtonShape,
                            backgroundColor = scheme.accent.copy(alpha = 0.10f),
                            spotlight = SpotlightPreset.ChipTab,
                            minTouchTarget = CouiMinTouchTarget,
                            onClick = { expanded = !expanded },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (expanded) "收起更新说明 ↑" else "查看更多更新说明 >",
                        kind = CouiTextKind.ItemValue,
                        color = scheme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 自绘进度条：250ms 平滑过渡填充满胶囊轨道，供弹窗复用。 */
@Composable
private fun CouiProgressBar(progress: Float) {
    val scheme = CouiKitTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = DOWNLOAD_PROGRESS_ANIM_MS),
        label = "coui-dialog-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CouiCapsuleShape)
            .background(scheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CouiCapsuleShape)
                .background(scheme.accent),
        )
    }
}

/** 更新说明正文：按块与行内 Markdown 解析，超链接可点击打开、代码与粗体保留语义着色。 */
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
    visibleBlocks.forEach { block ->
        when (block) {
            is MarkdownBlock.Heading -> {
                val styled = rememberCouiNotesInline(block.text)
                val kind = if (block.level <= 2) CouiTextKind.ItemTitle else CouiTextKind.Search
                CouiCustomStyledText(
                    text = styled,
                    style = rememberCouiTextStyle(kind),
                    textAlign = TextAlign.Start,
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
            is MarkdownBlock.Paragraph -> CouiBodyText(text = rememberCouiNotesInline(block.text))
        }
    }
}

@Composable
private fun NotesLine(prefix: String, text: String) {
    val styled = rememberCouiNotesInline(text)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.width(NotesPrefixMinWidth)) {
            CouiBodyText(text = prefix)
        }
        CouiBodyText(
            text = styled,
            modifier = Modifier.weight(1f),
        )
    }
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

/** 更新设置卡：自动检查开关与忽略当前版本。 */
@Composable
private fun UpdateSettingsGroup(
    autoCheckUpdate: Boolean,
    ignoreSummary: String,
    ignoreEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onIgnoreToggle: () -> Unit,
) {
    Group(title = "更新设置") {
        Item(
            title = { Text(text = "自动检查更新", kind = CouiTextKind.ItemTitle) },
            summary = { Text(text = "每次启动应用时自动检查最新版本", kind = CouiTextKind.ItemSummary) },
            trailing = {
                Switch(checked = autoCheckUpdate, onCheckedChange = onAutoCheckChange)
            },
        )
        Divider()
        Item(
            title = { Text(text = "忽略此版本", kind = CouiTextKind.ItemTitle) },
            summary = { Text(text = ignoreSummary, kind = CouiTextKind.ItemSummary) },
            enabled = ignoreEnabled,
            onClick = if (ignoreEnabled) onIgnoreToggle else null,
        )
    }
}

/** 版本信息与外部仓库入口。 */
@Composable
private fun UpdateMetaGroup(
    currentVersion: String,
    release: ReleaseInfo?,
    onOpenRelease: () -> Unit,
) {
    Group(title = "版本信息") {
        Item(
            title = { Text(text = "当前版本", kind = CouiTextKind.ItemTitle) },
            trailing = { Text(text = currentVersion, kind = CouiTextKind.ItemValue) },
        )
        Divider()
        Item(
            title = { Text(text = "远端最新版本", kind = CouiTextKind.ItemTitle) },
            trailing = { Text(text = release?.version ?: "—", kind = CouiTextKind.ItemValue) },
        )
        Divider()
        Item(
            title = { Text(text = "GitHub Releases", kind = CouiTextKind.ItemTitle) },
            summary = { Text(text = "在浏览器查看完整发布说明", kind = CouiTextKind.ItemSummary) },
            onClick = onOpenRelease,
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
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant,
                )
            }
            when (downloadState) {
                is UpdateDownloadState.Idle -> Text(
                    text = asset?.let { "安装包 " + it.name + assetSizeSuffix(it.size) }
                        ?: "该版本未附带安装包，请前往 Releases 页面手动下载。",
                    kind = CouiTextKind.ItemSummary,
                    color = scheme.onSurfaceVariant,
                )
                is UpdateDownloadState.Downloading -> {
                    CouiBodyText(
                        text = "正在下载 " + updateProgressText(downloadState, withSize = true),
                    )
                    CouiProgressBar(progress = downloadState.progress)
                }
                is UpdateDownloadState.Done -> CouiBodyText(
                    text = "安装包已下载完成，点击安装完成升级。",
                )
                is UpdateDownloadState.Failed -> {
                    CouiBodyText(text = downloadState.message, color = MiuixTheme.colorScheme.error)
                    CouiInlineButton(text = CouiStrings.Retry, onClick = onResetDownload)
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
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = if (emphasised) scheme.accent else scheme.accent.copy(alpha = 0.14f),
                // 强调色实底按库的主按钮口径取 OpaqueMedium1，浅底胶囊用 ChipTab
                spotlight = if (emphasised) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, kind = CouiTextKind.ItemValue, color = color)
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

internal fun couiUpdateButtonLabel(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
): String = when {
    state == null -> "正在检测更新…"
    downloadState is UpdateDownloadState.Downloading -> {
        val pct = if (downloadState.total > 0L) {
            ((downloadState.downloaded * 100L) / downloadState.total).toInt().coerceIn(0, 100)
        } else {
            (downloadState.progress * 100f).toInt().coerceIn(0, 100)
        }
        "正在下载… $pct%"
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
