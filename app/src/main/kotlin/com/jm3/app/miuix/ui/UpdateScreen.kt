package com.jm3.app.miuix.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.effect.BgEffectBackground
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCenter
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdateOutcome
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.UpdatePromptState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 检查更新页：视差头部 + 更新日志卡 + 更新设置卡 + 版本与操作卡。
 *
 * 取舍：
 * - 头部图标与标题随滚动淡出（阈值与 localsend/pixez 一致），滚动进度在 draw 阶段读取，
 *   避免每个像素的滚动位移都触发整页重组。
 * - 进入页面即检查一次，"自动检查更新"只控制启动期的静默检查；手动检查走同一入口。
 * - 下载状态复用 [ScreenDeps.updateDownloader]，页面只做展示与转发，不在本地维护第二套状态机。
 */
@Composable
fun UpdateScreen(deps: ScreenDeps, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val center = remember { UpdateCenter() }
    val applicationName = remember(context) { loadApplicationName(context) }
    val currentVersion = remember(context) { loadCurrentVersion(context) }

    var attempt by remember { mutableIntStateOf(0) }
    // null 表示正在检查：检查结论由 core 的 [UpdateOutcome] 承载，页面不再另立一套状态机
    var state by remember { mutableStateOf<UpdateOutcome?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var autoCheckUpdate by remember { mutableStateOf(true) }
    var ignoredVersion by remember { mutableStateOf<String?>(null) }
    val downloadState by deps.updateDownloader.state.collectAsState()

    val listState = rememberLazyListState()
    val heroHeightPx = remember { mutableFloatStateOf(1f) }
    val heroProgress = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / heroHeightPx.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }

    // 设置与检查放在同一个副作用里：拆成两个 LaunchedEffect 时检查可能读到尚未加载的忽略版本，
    // 导致已忽略的版本又弹一次窗。
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
        // 只有"该提醒"时才弹窗并记下本次会话已提示：与启动期检查共用同一条规则，
        // 免得启动期弹过的版本进页面又被弹一次。记录只存在内存里，下次冷启动仍会提醒。
        if (outcome is UpdateOutcome.Available && outcome.notify) {
            showDialog = true
            deps.updates.promptedVersion = outcome.release.version
        }
    }

    fun openReleasePage(url: String) {
        if (!openUpdateReleasePage(context, url)) {
            scope.launch { snackbarHostState.showSnackbar("无法打开链接：$url") }
        }
    }

    fun startDownload(release: ReleaseInfo) {
        val asset = release.apk ?: return
        deps.updateDownloader.start(asset)
    }

    fun installApk(file: File) {
        when (val result = ApkInstaller.install(context, file)) {
            is ApkInstaller.Result.Launched -> Unit
            is ApkInstaller.Result.PermissionRequired ->
                scope.launch { snackbarHostState.showSnackbar("请先允许本应用安装未知来源应用") }

            is ApkInstaller.Result.Failed ->
                scope.launch { snackbarHostState.showSnackbar(result.message) }
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        autoCheckUpdate = enabled
        scope.launch { deps.settings.setAutoCheckUpdate(enabled) }
    }

    fun toggleIgnoredVersion(latestVersion: String) {
        val next = UpdatePrompt.toggleIgnored(ignoredVersion, latestVersion)
        ignoredVersion = next
        scope.launch { deps.settings.setIgnoredUpdateVersion(next) }
    }

    val snapshot = state
    val latestRelease = when (snapshot) {
        is UpdateOutcome.Available -> snapshot.release
        is UpdateOutcome.UpToDate -> snapshot.release
        else -> null
    }
    val latestVersion = latestRelease?.version
    val hasNewVersion = snapshot is UpdateOutcome.Available
    val checking = snapshot == null
    val statusText = updateStatusText(currentVersion, snapshot)
    val statusColor = when (snapshot) {
        is UpdateOutcome.Available -> MiuixTheme.colorScheme.primary
        is UpdateOutcome.Failed -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "检查更新",
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BgEffectBackground(
            modifier = Modifier.fillMaxSize(),
            alpha = { 1f - heroProgress.value },
        ) {
            LazyColumn(
                state = listState,
                // 大屏把更新内容收进可读宽度并居中，背景特效仍铺满整屏
                modifier = Modifier.fillMaxSize().readableWidth(),
                contentPadding = innerPadding,
            ) {
                item(key = "hero") {
                    UpdateHero(
                        progress = heroProgress,
                        applicationName = applicationName,
                        statusText = statusText,
                        statusColor = statusColor,
                        onHeightChange = { heroHeightPx.floatValue = it.toFloat() },
                    )
                }
                item(key = "changelog") {
                    UpdateChangelogSection(
                        state = snapshot,
                        downloadState = downloadState,
                        onStartDownload = ::startDownload,
                        onCancelDownload = { deps.updateDownloader.cancel() },
                        onResetDownload = { deps.updateDownloader.reset() },
                        onInstall = ::installApk,
                        onOpenRelease = ::openReleasePage,
                        onRecheck = { attempt++ },
                    )
                }
                item(key = "settings") {
                    UpdateSettingsSection(
                        autoCheckUpdate = autoCheckUpdate,
                        ignoredVersion = ignoredVersion,
                        latestVersion = latestVersion,
                        checking = checking,
                        hasNewVersion = hasNewVersion,
                        onAutoCheckChange = ::setAutoCheck,
                        onIgnoreChange = { if (latestVersion != null) toggleIgnoredVersion(latestVersion) },
                    )
                }
                item(key = "actions") {
                    UpdateActionsSection(
                        currentVersion = currentVersion,
                        release = latestRelease,
                        checking = checking,
                        onRecheck = { attempt++ },
                        onOpenRelease = { openReleasePage(UpdateChecker.RELEASES_PAGE_URL) },
                    )
                }
            }
        }
    }

    val dialogRelease = (snapshot as? UpdateOutcome.Available)?.release
    // 下载落定（完成或失败）就收起弹窗：安装与重试都在页面的下载卡上，弹窗只负责把进度显示完。
    // key 用布尔而不是整个 state，否则每个进度 tick 都会重启一次 effect。
    val downloadSettled =
        downloadState is UpdateDownloadState.Done || downloadState is UpdateDownloadState.Failed
    LaunchedEffect(downloadSettled) {
        if (downloadSettled) showDialog = false
    }
    if (showDialog && dialogRelease != null) {
        UpdateAvailableDialog(
            show = showDialog,
            currentVersion = currentVersion,
            release = dialogRelease,
            downloadState = downloadState,
            onDismiss = { showDialog = false },
            // 不关弹窗：刚点下去的主按钮原地变成"下载中 xx%"，进度不必回页面才看得到
            onDownload = { startDownload(dialogRelease) },
            onOpenRelease = {
                showDialog = false
                openReleasePage(dialogRelease.htmlUrl)
            },
            onIgnore = { version ->
                showDialog = false
                ignoredVersion = version
                scope.launch { deps.settings.setIgnoredUpdateVersion(version) }
            },
            onCancelDownload = { deps.updateDownloader.cancel() },
        )
    }
}

/**
 * 头部：应用图标 + 应用名 + 状态行，随滚动依次淡出。
 *
 * 三段阈值取自 localsend/pixez（状态行 0.05~0.20、应用名 0.20~0.35、图标 0.35~0.50），
 * 让信息自下而上递减而不是整块消失。
 */
@Composable
private fun UpdateHero(
    progress: State<Float>,
    applicationName: String,
    statusText: String,
    statusColor: Color,
    onHeightChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChange(it.height) }
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(progress = progress)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = applicationName,
            modifier = Modifier.graphicsLayer { alpha = fadeOut(progress.value, 0.20f, 0.35f) },
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = statusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = fadeOut(progress.value, 0.05f, 0.20f) },
            style = MiuixTheme.textStyles.body2,
            color = statusColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppIcon(progress: State<Float>) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { AppIconInnerSize.roundToPx() }
    val icon = remember(context, sizePx) { loadAppIcon(context, sizePx) }
    Box(
        modifier = Modifier
            .size(AppIconSize)
            .graphicsLayer {
                val fade = fadeOut(progress.value, 0.35f, 0.50f)
                alpha = fade
                val scale = 1f - (1f - fade) * 0.05f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(AppIconCorner))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(AppIconInnerSize),
            )
        }
    }
}

/** 更新日志卡：按状态渲染说明、下载进度与主操作。 */
@Composable
private fun UpdateChangelogSection(
    state: UpdateOutcome?,
    downloadState: UpdateDownloadState,
    onStartDownload: (ReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: (String) -> Unit,
    onRecheck: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = changelogTitle(state))
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在检查更新…",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    is UpdateOutcome.Failed -> {
                        Text(
                            text = state.message,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "重试", onClick = onRecheck)
                            TextButton(
                                text = "在浏览器打开 Releases 页",
                                onClick = { onOpenRelease(UpdateChecker.RELEASES_PAGE_URL) },
                            )
                        }
                    }

                    is UpdateOutcome.UpToDate -> {
                        ReleaseNotes(state.release)
                    }

                    is UpdateOutcome.Available -> {
                        ReleaseNotes(state.release)
                        DownloadArea(
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
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ReleaseNotes(release: ReleaseInfo) {
    Text(
        text = release.name.ifBlank { "版本特性说明" },
        style = MiuixTheme.textStyles.body1,
        fontWeight = FontWeight.Bold,
    )
    MarkdownNotes(markdown = release.notes, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DownloadArea(
    release: ReleaseInfo,
    downloadState: UpdateDownloadState,
    onStartDownload: (ReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val asset = release.apk
    if (asset == null) {
        Text(
            text = "该版本未附带安装包，请前往 Releases 页面手动下载。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Button(
            onClick = { onOpenRelease(release.htmlUrl) },
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "前往 Releases 页面")
        }
        return
    }

    Text(
        text = "安装包 ${asset.name}${assetSizeSuffix(asset.size)}",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    when (downloadState) {
        UpdateDownloadState.Idle -> Button(
            onClick = { onStartDownload(release) },
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "立即下载更新")
        }

        is UpdateDownloadState.Downloading -> {
            Text(
                text = "正在下载 ${updateProgressText(downloadState, withSize = true)}",
                style = MiuixTheme.textStyles.body2,
            )
            LinearProgressIndicator(
                progress = downloadState.progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (downloadState.speedBytesPerSec > 0L) {
                    "速度 ${formatUpdateSpeed(downloadState.speedBytesPerSec)}"
                } else {
                    "正在计算速度…"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Button(
                onClick = onCancelDownload,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "取消下载")
            }
        }

        is UpdateDownloadState.Done -> {
            Text(text = "安装包已下载完成，点击安装完成升级。", style = MiuixTheme.textStyles.body2)
            Button(
                onClick = { onInstall(downloadState.file) },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "立即安装")
            }
            TextButton(
                text = "重新下载",
                onClick = onResetDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is UpdateDownloadState.Failed -> {
            Text(
                text = downloadState.message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
            Button(
                onClick = { onStartDownload(release) },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "重试")
            }
            // 应用内重试仍失败时留一个不依赖下载器的出口：直接开安装包直链或发布页
            TextButton(
                text = "用浏览器下载",
                onClick = { onOpenRelease(updateBrowserFallbackUrl(release)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UpdateSettingsSection(
    autoCheckUpdate: Boolean,
    ignoredVersion: String?,
    latestVersion: String?,
    checking: Boolean,
    hasNewVersion: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onIgnoreChange: () -> Unit,
) {
    val skin = LocalSkin.current
    val ignored = latestVersion != null && UpdatePrompt.isIgnored(ignoredVersion, latestVersion)
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = "更新设置")
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
            SwitchPreference(
                title = "自动检查更新",
                summary = "每次启动应用时自动检查最新版本",
                checked = autoCheckUpdate,
                onCheckedChange = onAutoCheckChange,
            )
            SwitchPreference(
                title = "忽略此版本",
                summary = when {
                    checking -> "正在检查更新…"
                    latestVersion == null -> "尚未获取远端版本"
                    !hasNewVersion -> "当前没有需要忽略的新版本"
                    ignored -> "已忽略 $latestVersion，不再弹出该版本提示"
                    else -> "忽略 $latestVersion 的更新提示"
                },
                checked = ignored,
                onCheckedChange = { onIgnoreChange() },
                enabled = hasNewVersion && latestVersion != null,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun UpdateActionsSection(
    currentVersion: String,
    release: ReleaseInfo?,
    checking: Boolean,
    onRecheck: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = "版本与操作")
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
            BasicComponent(
                title = "手动检查更新",
                summary = if (checking) "正在检索远端版本信息…" else "点击从 GitHub Releases 检索最新版本",
                onClick = onRecheck,
                endActions = {
                    if (checking) InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                },
            )
            ArrowPreference(
                title = "GitHub Releases",
                summary = "在浏览器查看完整发布说明",
                onClick = onOpenRelease,
            )
            BasicComponent(title = "当前版本", summary = currentVersion)
            BasicComponent(
                title = "远端最新版本",
                summary = when {
                    release == null -> "尚未获取"
                    release.publishedDate.isNotBlank() -> "${release.version} · 发布于 ${release.publishedDate}"
                    else -> release.version
                },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 说明卡标题：有新版本时带上目标版本，已是最新时退回当前版本说明。 */
private fun changelogTitle(state: UpdateOutcome?): String = when (state) {
    is UpdateOutcome.Available -> "更新说明（${state.release.version}）"
    is UpdateOutcome.UpToDate -> "当前版本说明（${state.release.version}）"
    else -> "更新说明"
}

/** 滚动进度 [from]~[to] 区间的淡出系数：区间外分别为全显/全隐，中途线性过渡。 */
private fun fadeOut(progress: Float, from: Float, to: Float): Float {
    val span = (to - from).coerceAtLeast(0.001f)
    return 1f - ((progress - from) / span).coerceIn(0f, 1f)
}

/** 在系统浏览器打开链接，返回是否成功拉起；失败由调用方决定提示或回退。 */
internal fun openUpdateReleasePage(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess

private fun assetSizeSuffix(size: Long): String = if (size > 0L) "（${formatUpdateSize(size)}）" else ""

internal fun formatUpdateSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    return when {
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

internal fun formatUpdateSpeed(bytesPerSec: Long): String = "${formatUpdateSize(bytesPerSec)}/s"

private val AppIconSize = 88.dp
private val AppIconInnerSize = 56.dp
private val AppIconCorner = 24.dp

private fun loadAppIcon(context: Context, sizePx: Int): ImageBitmap? = runCatching {
    val drawable = context.packageManager.getApplicationIcon(context.packageName)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap.asImageBitmap()
}.getOrNull()

@Suppress("DEPRECATION")
private fun loadApplicationName(context: Context): String =
    runCatching {
        context.packageManager.getApplicationInfo(context.packageName, 0)
            .loadLabel(context.packageManager)
            .toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "JMComic MIUIX"

@Suppress("DEPRECATION")
internal fun loadCurrentVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "未知"
