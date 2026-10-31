package com.jm3.app.miuix.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCheckResult
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.VersionRelation
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
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
     * 此时只做说明，不提示“降级”。
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
 * 检查更新页（GitHub Releases）。
 *
 * 取舍：
 * - 只打开 Release 页面（html_url），不做应用内 APK 下载，也不用 FileProvider 或
 *   REQUEST_INSTALL_PACKAGES：安装包由 GitHub 页面交给系统浏览器下载，权限与
 *   下载器生命周期都不归本应用。
 * - 更新说明是 Markdown 原文，仓库没有 Markdown 渲染器，按纯文本展示（保留换行）。
 * - 状态用 remember + LaunchedEffect 持有，没有 ViewModel；网络请求在
 *   [UpdateChecker] 内部的 Dispatchers.IO 上执行。
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
    val currentVersion = remember {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "未知"
    }
    // attempt 只做“重新检查”的触发器：递增即重新进入 LaunchedEffect
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }

    LaunchedEffect(attempt) {
        state = UpdateUiState.Checking
        state = when (val result = checker.check(currentVersion)) {
            is UpdateCheckResult.Success -> when (result.relation) {
                VersionRelation.NEWER -> UpdateUiState.Available(result.release, currentVersion)
                VersionRelation.SAME -> UpdateUiState.UpToDate(currentVersion, result.release.version, localAhead = false)
                VersionRelation.OLDER -> UpdateUiState.UpToDate(currentVersion, result.release.version, localAhead = true)
            }

            is UpdateCheckResult.Failed -> UpdateUiState.Failed(result.message)
        }
    }

    /** 打开 Release 页面；没有可用浏览器时回落到 Snackbar，不静默失败。 */
    fun openReleasePage(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                scope.launch { snackbarHostState.showSnackbar("无法打开链接：$url") }
            }
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
                    state = current,
                    onOpenRelease = { openReleasePage(current.release.htmlUrl) },
                    onRecheck = { attempt++ },
                )
            }
        }
    }
}

@Composable
private fun UpToDateContent(
    state: UpdateUiState.UpToDate,
    onRecheck: () -> Unit,
) {
    SmallTitle(text = "版本状态")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicComponent(
            title = "已是最新版本",
            summary = if (state.localAhead) {
                "本地 $state.currentVersion 高于远端发布 $state.remoteVersion"
            } else {
                "当前版本 $state.currentVersion"
            },
        )
        BasicComponent(
            title = "远端最新",
            summary = state.remoteVersion,
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

@Composable
private fun AvailableContent(
    state: UpdateUiState.Available,
    onOpenRelease: () -> Unit,
    onRecheck: () -> Unit,
) {
    val release = state.release
    SmallTitle(text = "发现新版本")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicComponent(
            title = "最新版本 ${release.version}",
            summary = release.name.ifBlank { "GitHub Release ${release.tagName}" },
        )
        BasicComponent(
            title = "当前版本",
            summary = state.currentVersion,
        )
        if (release.publishedDate.isNotBlank()) {
            BasicComponent(
                title = "发布时间",
                summary = release.publishedDate,
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    SmallTitle(text = "更新说明")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // 仓库无 Markdown 渲染器：原文展示，保留换行与列表符号
        Text(
            text = release.notes.ifBlank { "该版本未提供更新说明。" },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            style = MiuixTheme.textStyles.body2,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onOpenRelease,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(text = "前往下载")
    }
    Text(
        text = "将在浏览器中打开 GitHub Release 页面",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
    )
    TextButton(
        text = "重新检查",
        onClick = onRecheck,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(modifier = Modifier.height(24.dp))
}
