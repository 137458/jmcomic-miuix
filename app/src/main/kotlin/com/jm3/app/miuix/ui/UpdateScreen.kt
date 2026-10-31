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
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.effect.BgEffectBackground
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCheckResult
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.VersionRelation
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

/** 检查更新页的四态：检查中 / 已是最新 / 发现新版本 / 失败可重试。 */
private sealed interface UpdateUiState {
    data object Checking : UpdateUiState

    /** 远端不高于本地。仍带 [release]，用于显示当前版本的发布说明。 */
    data class UpToDate(
        val currentVersion: String,
        val release: ReleaseInfo,
        val localAhead: Boolean,
    ) : UpdateUiState

    data class Available(val release: ReleaseInfo, val currentVersion: String) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState
}

/**
 * 检查更新页：视差头部 + 更新日志卡 + 更新设置卡 + 版本与操作卡。
 *
 * 取舍：
 * - 头部图标与标题随滚动淡出（阈值与 localsend/pixez 一致），滚动进度在 draw 阶段读取，
 *   避免每个像素的滚动位移都触发整页重组。
 * - 进入页面即检查一次，"自动检查更新"只控制启动期的静默检查；手动检查走同一入口。
 * - 下载状态复用 [Jm3Graph.updateDownloader]，页面只做展示与转发，不在本地维护第二套状态机。
 */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val checker = remember { UpdateChecker() }
    val applicationName = remember(context) { loadApplicationName(context) }
    val currentVersion = remember(context) { loadCurrentVersion(context) }

    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }
    var showDialog by remember { mutableStateOf(false) }
    var autoCheckUpdate by remember { mutableStateOf(true) }
    var ignoredVersion by remember { mutableStateOf<String?>(null) }
    val downloadState by Jm3Graph.updateDownloader.state.collectAsState()

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
        autoCheckUpdate = Jm3Graph.settings.autoCheckUpdate()
        ignoredVersion = Jm3Graph.settings.ignoredUpdateVersion()
        state = UpdateUiState.Checking
        showDialog = false
        state = when (val result = checker.check(currentVersion)) {
            is UpdateCheckResult.Success -> when (result.relation) {
                VersionRelation.NEWER -> UpdateUiState.Available(result.release, currentVersion)
                VersionRelation.SAME -> UpdateUiState.UpToDate(currentVersion, result.release, localAhead = false)
                VersionRelation.OLDER -> UpdateUiState.UpToDate(currentVersion, result.release, localAhead = true)
            }

            is UpdateCheckResult.Failed -> UpdateUiState.Failed(result.message)
        }
        (state as? UpdateUiState.Available)?.let { available ->
            val latest = available.release.version
            if (!UpdatePrompt.isIgnored(ignoredVersion, latest)) {
                showDialog = true
                // 记下已弹过：同一版本在本进程内不重复打扰，冷启动时由启动期检查决定
                Jm3Graph.settings.setPromptedUpdateVersion(latest)
            }
        }
    }

    fun openReleasePage(url: String) {
        if (!openUpdateReleasePage(context, url)) {
            scope.launch { snackbarHostState.showSnackbar("无法打开链接：$url") }
        }
    }

    fun startDownload(release: ReleaseInfo) {
        val asset = release.apk ?: return
        Jm3Graph.updateDownloader.start(context, asset)
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
        scope.launch { Jm3Graph.settings.setAutoCheckUpdate(enabled) }
    }

    fun toggleIgnoredVersion(latestVersion: String) {
        val next = UpdatePrompt.toggleIgnored(ignoredVersion, latestVersion)
        ignoredVersion = next
        scope.launch { Jm3Graph.settings.setIgnoredUpdateVersion(next) }
    }

    val snapshot = state
    val latestRelease = when (snapshot) {
        is UpdateUiState.Available -> snapshot.release
        is UpdateUiState.UpToDate -> snapshot.release
        else -> null
    }
    val latestVersion = latestRelease?.version
    val hasNewVersion = snapshot is UpdateUiState.Available
    val checking = snapshot is UpdateUiState.Checking
    val statusText = when (snapshot) {
        UpdateUiState.Checking -> "正在检查更新…"
        is UpdateUiState.Available -> "发现新版本 ${snapshot.release.version}（当前 $currentVersion）"
        is UpdateUiState.UpToDate ->
            if (snapshot.localAhead) "本地版本更高（远端 ${snapshot.release.version}）" else "已是最新版本（v$currentVersion）"

        is UpdateUiState.Failed -> "检查更新失败"
    }
    val statusColor = when (snapshot) {
        is UpdateUiState.Available -> MiuixTheme.colorScheme.primary
        is UpdateUiState.Failed -> MiuixTheme.colorScheme.error
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
                modifier = Modifier.fillMaxSize(),
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
                        onCancelDownload = { Jm3Graph.updateDownloader.cancel() },
                        onResetDownload = { Jm3Graph.updateDownloader.reset() },
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

    val dialogRelease = (snapshot as? UpdateUiState.Available)?.release
    if (showDialog && dialogRelease != null && downloadState !is UpdateDownloadState.Downloading) {
        UpdateAvailableDialog(
            show = showDialog,
            currentVersion = currentVersion,
            release = dialogRelease,
            onDismiss = { showDialog = false },
            onDownload = {
                showDialog = false
                startDownload(dialogRelease)
            },
            onOpenRelease = {
                showDialog = false
                openReleasePage(dialogRelease.htmlUrl)
            },
            onIgnore = { version ->
                showDialog = false
                ignoredVersion = version
                scope.launch { Jm3Graph.settings.setIgnoredUpdateVersion(version) }
            },
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
            .background(MiuixTheme.colorScheme.surfaceVariant),
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
    state: UpdateUiState,
    downloadState: UpdateDownloadState,
    onStartDownload: (ReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: (String) -> Unit,
    onRecheck: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = changelogTitle(state))
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    UpdateUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在检查更新…",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    is UpdateUiState.Failed -> {
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

                    is UpdateUiState.UpToDate -> {
                        ReleaseNotes(state.release)
                    }

                    is UpdateUiState.Available -> {
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
                text = "正在下载 ${downloadProgressText(downloadState)}",
                style = MiuixTheme.textStyles.body2,
            )
            LinearProgressIndicator(
                progress = downloadState.progress.coerceIn(0f, 1f),
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
    val ignored = latestVersion != null && UpdatePrompt.isIgnored(ignoredVersion, latestVersion)
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = "更新设置")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = "版本与操作")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
private fun changelogTitle(state: UpdateUiState): String = when (state) {
    is UpdateUiState.Available -> "更新说明（${state.release.version}）"
    is UpdateUiState.UpToDate -> "当前版本说明（${state.release.version}）"
    else -> "更新说明"
}

/** 下载进度文案：总长未知时只报已下字节，避免出现一直 0% 的观感。 */
private fun downloadProgressText(state: UpdateDownloadState.Downloading): String {
    val percent = (state.progress.coerceIn(0f, 1f) * 100).toInt()
    return if (state.total > 0L) {
        "${formatUpdateSize(state.total)}（$percent）"
    } else {
        "${formatUpdateSize(state.downloaded)}（$percent）"
    }
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
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
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
