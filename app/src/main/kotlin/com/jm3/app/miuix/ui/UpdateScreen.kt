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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseAsset
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCheckResult
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.VersionRelation
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 检查更新页的四态：检查中 / 已是最新 / 发现新版本 / 失败可重试。 */
private sealed interface UpdateUiState {

    /** 请求在途 */
    data object Checking : UpdateUiState

    /**
     * 无需更新。[localAhead] 表示本地版本高于远端（装了开发版/预发布版的情况），
     * 此时只做说明，不提示"降级"。
     */
    data class UpToDate(
        val currentVersion: String,
        val remoteVersion: String,
        val localAhead: Boolean,
    ) : UpdateUiState

    /** 远端更高：展示版本、发布时间、更新说明与下载入口 */
    data class Available(
        val release: ReleaseInfo,
        val currentVersion: String,
    ) : UpdateUiState

    /** 请求失败：展示原因与重试入口 */
    data class Failed(val message: String) : UpdateUiState
}

/**
 * 检查更新页（GitHub Releases）：Hero 区 + 版本信息 + Markdown 更新说明 + 应用内下载安装。
 *
 * 取舍：
 * - 下载与安装拆到 [Jm3Graph.updateDownloader] / [ApkInstaller]（进程级状态）。页面只观察状态、
 *   发起动作，因此离开页面再回来下载不会中断，也不会因为重组重复触发下载（start 内部幂等）。
 * - 状态仍用 remember + LaunchedEffect 持有，没有 ViewModel：检查更新是一次性的网络动作，
 *   页面销毁后重进重新检查即可，不需要跨进程状态保存。
 * - 下载结果（Done/Failed）不随"重新检查"清空。清空会删掉已下载的安装包；而同一进程内
 *   远端版本恰好变化的概率极低，宁可保留安装包也不做隐式删除。
 * - 已安装版本只能从 PackageManager 读回（构建期 versionName 是唯一来源）。
 *
 * @param onBack 返回上一级
 */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val checker = remember { UpdateChecker() }
    val applicationName = remember(context) { loadApplicationName(context) }
    val currentVersion = remember(context) { loadCurrentVersion(context) }
    // attempt 只做"重新检查"的触发器：递增即重新进入 LaunchedEffect
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }
    // 发现新版本时先弹一次提示框，页面本身保留完整更新说明作为详情
    var showDialog by remember { mutableStateOf(false) }
    val downloadState by Jm3Graph.updateDownloader.state.collectAsState()

    LaunchedEffect(attempt) {
        state = UpdateUiState.Checking
        showDialog = false
        state = when (val result = checker.check(currentVersion)) {
            is UpdateCheckResult.Success -> when (result.relation) {
                VersionRelation.NEWER -> UpdateUiState.Available(result.release, currentVersion)
                VersionRelation.SAME -> UpdateUiState.UpToDate(currentVersion, result.release.version, localAhead = false)
                VersionRelation.OLDER -> UpdateUiState.UpToDate(currentVersion, result.release.version, localAhead = true)
            }

            is UpdateCheckResult.Failed -> UpdateUiState.Failed(result.message)
        }
        showDialog = state is UpdateUiState.Available
    }

    /** 打开 Release 页面；没有可用浏览器时回落到 Snackbar，不静默失败。 */
    fun openReleasePage(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                scope.launch { snackbarHostState.showSnackbar("无法打开链接：$url") }
            }
    }

    /** 只有存在 .apk 资产时才允许应用内下载；没有安装包的分支不会走到这里（按钮另有出口）。 */
    fun startDownload(release: ReleaseInfo) {
        val asset = release.apk ?: return
        Jm3Graph.updateDownloader.start(context, asset)
    }

    /** 安装结果按 [ApkInstaller.Result] 分支提示：授权页与失败原因都要让用户看见，不静默吞掉。 */
    fun installApk(file: File) {
        when (val result = ApkInstaller.install(context, file)) {
            ApkInstaller.Result.Launched -> Unit
            ApkInstaller.Result.PermissionRequired -> scope.launch {
                snackbarHostState.showSnackbar("请先允许本应用安装未知来源应用")
            }

            is ApkInstaller.Result.Failed -> scope.launch {
                snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    val (statusText, statusColor) = when (val current = state) {
        UpdateUiState.Checking -> "正在检查更新…" to MiuixTheme.colorScheme.onSurfaceVariantSummary
        is UpdateUiState.Available -> "发现新版本 ${current.release.version}" to MiuixTheme.colorScheme.primary
        is UpdateUiState.UpToDate -> "已是最新版本" to MiuixTheme.colorScheme.onSurfaceVariantSummary
        is UpdateUiState.Failed -> "检查失败" to MiuixTheme.colorScheme.error
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "检查更新",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏是浮层：内容从顶栏下沿起排
            TopBarContentSpacer(paddingValues = innerPadding)
            UpdateHero(
                applicationName = applicationName,
                statusText = statusText,
                statusColor = statusColor,
            )
            when (val current = state) {
                UpdateUiState.Checking -> LoadingScreen(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    text = "正在检查更新…",
                )

                is UpdateUiState.Failed -> {
                    ErrorScreen(
                        message = current.message,
                        onRetry = { attempt++ },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                    // 限流与断网不该把用户堵在页面上：Release 页在浏览器里同样可读
                    TextButton(
                        text = "在浏览器打开 Releases 页",
                        onClick = { openReleasePage(UpdateChecker.RELEASES_PAGE_URL) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                is UpdateUiState.UpToDate -> UpToDateContent(
                    state = current,
                    onRecheck = { attempt++ },
                )

                is UpdateUiState.Available -> AvailableContent(
                    release = current.release,
                    currentVersion = current.currentVersion,
                    downloadState = downloadState,
                    onStartDownload = { startDownload(current.release) },
                    onCancelDownload = { Jm3Graph.updateDownloader.cancel() },
                    onResetDownload = { Jm3Graph.updateDownloader.reset() },
                    onInstall = ::installApk,
                    onOpenRelease = { openReleasePage(current.release.htmlUrl) },
                    onRecheck = { attempt++ },
                )
            }
        }
        // 下载进行中不弹提示框：弹窗会与页面各显示一份进度，且弹窗会遮挡下载按钮
        if (showDialog && downloadState !is UpdateDownloadState.Downloading) {
            (state as? UpdateUiState.Available)?.let { current ->
                UpdateAvailableDialog(
                    show = true,
                    currentVersion = current.currentVersion,
                    release = current.release,
                    onDismiss = { showDialog = false },
                    onDownload = {
                        showDialog = false
                        startDownload(current.release)
                    },
                    onOpenRelease = {
                        showDialog = false
                        openReleasePage(current.release.htmlUrl)
                    },
                )
            }
        }
    }
}

/**
 * Hero 区：应用图标 + 应用名 + 一行状态文案。
 *
 * 取舍：不做滚动淡出。顶栏折叠动画由 GlassScaffold 驱动，Hero 若再叠加一层自己的淡出，
 * 两套动画的节奏对不齐，滚动中途会看到图标忽明忽暗；静态区在长页面上也更容易一屏读完。
 */
@Composable
private fun UpdateHero(applicationName: String, statusText: String, statusColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = applicationName,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statusText,
            style = MiuixTheme.textStyles.body2,
            color = statusColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

/**
 * 应用图标容器：圆角方块 + 真实桌面图标。
 *
 * 取舍：不用 painterResource(R.mipmap.ic_launcher)。本工程的 launcher 图标只有
 * mipmap-anydpi-v26 下的 adaptive-icon XML，BitmapFactory 解不出位图，Compose 的
 * painterResource 会因此抛异常；改为向 PackageManager 要系统拼装好的 Drawable，
 * 自己按容器像素尺寸画进位图，既不会崩，也保留了自适应图标（背景 + 前景）的完整外观。
 */
@Composable
private fun AppIcon() {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { AppIconSize.roundToPx() }
    val icon = remember(context, sizePx) { loadAppIcon(context, sizePx) }
    Box(
        modifier = Modifier
            .size(AppIconSize)
            .clip(RoundedCornerShape(AppIconCorner))
            .background(MiuixTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // 取不到图标时只留一块圆角底色：图标是装饰信息，不值得为它在页面上报错
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(AppIconSize),
            )
        }
    }
}

/** 已是最新：只保留"当前 / 远端"两行事实，不做版本跃迁的箭头强调。 */
@Composable
private fun UpToDateContent(state: UpdateUiState.UpToDate, onRecheck: () -> Unit) {
    SmallTitle(text = "版本状态")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicComponent(
            title = "当前版本",
            summary = state.currentVersion,
        )
        BasicComponent(
            title = "远端最新",
            summary = if (state.localAhead) {
                // 本地领先时补一句说明，避免用户以为"远端最新"比手上版本还新
                "${state.remoteVersion}（本地版本更高）"
            } else {
                state.remoteVersion
            },
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    TextButton(
        text = "重新检查",
        onClick = onRecheck,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * 发现新版本：版本信息卡 + 更新说明卡 + 下载安装区 + 常驻出口。
 *
 * 各区块拆开是为了让"下载状态"只影响下载区：进度刷新不会重建版本卡与更新说明，
 * 长 Markdown 也不会随进度条一起重排。
 */
@Composable
private fun AvailableContent(
    release: ReleaseInfo,
    currentVersion: String,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: () -> Unit,
    onRecheck: () -> Unit,
) {
    SmallTitle(text = "版本信息")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicComponent(title = "当前版本", summary = currentVersion)
        BasicComponent(title = "最新版本", summary = release.version)
        if (release.publishedDate.isNotBlank()) {
            BasicComponent(title = "发布时间", summary = release.publishedDate)
        }
        val apkSize = release.apk?.size ?: 0L
        // size 为 0 表示 GitHub 未给出字节数（未知），不是空包，因此只在已知时展示这一行
        if (apkSize > 0L) {
            BasicComponent(title = "安装包大小", summary = formatUpdateSize(apkSize))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    SmallTitle(text = "更新说明")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        MarkdownNotes(
            markdown = release.notes,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    SmallTitle(text = "下载与安装")
    DownloadSection(
        asset = release.apk,
        downloadState = downloadState,
        onStartDownload = onStartDownload,
        onCancelDownload = onCancelDownload,
        onResetDownload = onResetDownload,
        onInstall = onInstall,
        onOpenRelease = onOpenRelease,
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Release 页面是最终兜底出口（下载失败、无安装包、只想看网页），与重新检查常驻并排
        TextButton(
            text = "前往 GitHub Releases",
            onClick = onOpenRelease,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = "重新检查",
            onClick = onRecheck,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * 下载与安装区：按 [UpdateDownloadState] 四态给动作，外加"没有安装包"的独立出口。
 *
 * 分支必须完整：Idle 给下载、Downloading 给进度与取消、Done 给安装与重新下载、
 * Failed 给错误原因与重试；缺任何一支都会让用户卡在无法前进的界面上。
 */
@Composable
private fun DownloadSection(
    asset: ReleaseAsset?,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onResetDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onOpenRelease: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (asset == null) {
                // GitHub 上该 Release 没有 .apk，应用内下载无从谈起，只剩浏览器出口
                Text(
                    text = "该版本未附带安装包，请前往 Releases 页面手动下载。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Button(
                    onClick = onOpenRelease,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "前往 Releases 页面")
                }
                return@Column
            }

            when (downloadState) {
                UpdateDownloadState.Idle -> {
                    Text(
                        text = "安装包 ${asset.name}${assetSizeSuffix(asset.size)}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "下载安装包")
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    val percent = if (downloadState.total > 0L) {
                        "${(downloadState.progress * 100).toInt()}%"
                    } else {
                        null
                    }
                    val sizeText = if (downloadState.total > 0L) {
                        "${formatUpdateSize(downloadState.downloaded)} / ${formatUpdateSize(downloadState.total)}"
                    } else {
                        // 服务器没给长度（分块传输）时进度恒为 0，只能展示已下载量
                        formatUpdateSize(downloadState.downloaded)
                    }
                    Text(
                        text = if (percent == null) "正在下载 $sizeText" else "正在下载 $sizeText（$percent）",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
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
                    Text(
                        text = "安装包已下载完成，点击安装完成升级。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
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
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "重试")
                    }
                }
            }
        }
    }
}

/** 安装包大小的括号后缀：未知（0）时留空，避免出现"0 B"这类误导性数字。 */
private fun assetSizeSuffix(size: Long): String =
    if (size > 0L) "（${formatUpdateSize(size)}）" else ""

/** 字节数展示：下载量与速度共用一套单位，保证同一屏内的刻度一致。 */
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

/** 速度展示：单位与 [formatUpdateSize] 一致，追加每秒下标。 */
internal fun formatUpdateSpeed(bytesPerSec: Long): String = "${formatUpdateSize(bytesPerSec)}/s"

/** 应用图标容器尺寸与圆角：64dp 足够压住 Hero 区，又不会把状态文案挤到第二屏。 */
private val AppIconSize = 64.dp
private val AppIconCorner = 16.dp

/** 把系统拼好的自适应图标画进位图；任何一步失败都返回 null，由调用方退化为纯色块。 */
private fun loadAppIcon(context: Context, sizePx: Int): ImageBitmap? = runCatching {
    val drawable = context.packageManager.getApplicationIcon(context.packageName)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap.asImageBitmap()
}.getOrNull()

/** 应用名从 PackageManager 读回，读不到时用工程固定名兜底，不用硬编码替代真实名称。 */
@Suppress("DEPRECATION")
private fun loadApplicationName(context: Context): String = runCatching {
    context.packageManager
        .getApplicationInfo(context.packageName, 0)
        .loadLabel(context.packageManager)
        .toString()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "JMComic MIUIX"

/** 已安装版本号；读不到（极少数系统限制场景）时显示"未知"，不伪造 0.0.0。 */
@Suppress("DEPRECATION")
private fun loadCurrentVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "未知"
