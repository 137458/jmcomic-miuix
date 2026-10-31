package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCheckResult
import com.jm3.core.update.UpdateChecker
import com.jm3.core.update.UpdatePrompt
import com.jm3.core.update.VersionRelation
import kotlinx.coroutines.launch

/**
 * 启动期的静默检查：开启"自动检查更新"时拉取一次远端最新版本，有新版本且未被忽略/未提示过才弹窗。
 *
 * 取舍：
 * - 只在冷启动跑一次，且受"自动检查更新"开关控制；主动检查仍由更新页负责。
 * - 提示过即记录版本号，避免同一版本每次启动都打扰；用户忽略的版本则永久静默。
 * - 下载动作不在弹窗内实现：交给更新页统一的下载状态机，避免出现第二套进度状态。
 */
@Composable
internal fun UpdateStartupPrompt(onOpenUpdatePage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) { loadCurrentVersion(context) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        if (!Jm3Graph.settings.autoCheckUpdate()) return@LaunchedEffect
        val success = UpdateChecker().check(currentVersion) as? UpdateCheckResult.Success
            ?: return@LaunchedEffect
        if (success.relation != VersionRelation.NEWER) return@LaunchedEffect
        val latest = success.release.version
        val shouldNotify = UpdatePrompt.shouldNotify(
            latestVersion = latest,
            ignoredVersion = Jm3Graph.settings.ignoredUpdateVersion(),
            promptedVersion = Jm3Graph.settings.promptedUpdateVersion(),
        )
        if (!shouldNotify) return@LaunchedEffect
        Jm3Graph.settings.setPromptedUpdateVersion(latest)
        release = success.release
    }

    val pending = release ?: return
    UpdateAvailableDialog(
        show = true,
        currentVersion = currentVersion,
        release = pending,
        onDismiss = { release = null },
        onDownload = {
            release = null
            pending.apk?.let { asset -> Jm3Graph.updateDownloader.start(context, asset) }
            onOpenUpdatePage()
        },
        onOpenRelease = {
            release = null
            if (!openUpdateReleasePage(context, pending.htmlUrl)) onOpenUpdatePage()
        },
        onIgnore = { version ->
            release = null
            scope.launch { Jm3Graph.settings.setIgnoredUpdateVersion(version) }
        },
    )
}
