package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jm3.app.miuix.ScreenDeps
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateCenter
import com.jm3.core.update.UpdatePromptState
import kotlinx.coroutines.launch

/**
 * 启动期的静默检查：开启"自动检查更新"时拉取一次远端最新版本，有新版本且未被忽略/未提示过才弹窗。
 *
 * 取舍：
 * - 只在冷启动跑一次，且受"自动检查更新"开关控制；主动检查仍由更新页负责。
 * - "该不该弹"不在页面里判：整条规则（版本关系 + 忽略 + 已提示）由 [UpdateCenter.promptForStartup]
 *   给出结论，更新页走同一个入口，两处不会各自漂移。
 * - 提示过即记录版本号，避免同一版本每次启动都打扰；用户忽略的版本则永久静默。
 * - 下载动作不在弹窗内实现：交给更新页统一的下载状态机，避免出现第二套进度状态。
 */
@Composable
internal fun UpdateStartupPrompt(deps: ScreenDeps, onOpenUpdatePage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) { loadCurrentVersion(context) }
    val center = remember { UpdateCenter() }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        val pending = center.promptForStartup(
            autoCheckEnabled = deps.settings.autoCheckUpdate(),
            localVersion = currentVersion,
            prompt = UpdatePromptState(
                ignoredVersion = deps.settings.ignoredUpdateVersion(),
                promptedVersion = deps.updates.promptedVersion,
            ),
        ) ?: return@LaunchedEffect
        // 先记账再弹窗：本次会话内不再重复弹。记录只在内存里，点「稍后」不会变成永久忽略。
        deps.updates.promptedVersion = pending.version
        release = pending
    }

    val pending = release ?: return
    UpdateAvailableDialog(
        show = true,
        currentVersion = currentVersion,
        release = pending,
        onDismiss = { release = null },
        onDownload = {
            release = null
            pending.apk?.let { asset -> deps.updateDownloader.start(asset) }
            onOpenUpdatePage()
        },
        onOpenRelease = {
            release = null
            if (!openUpdateReleasePage(context, pending.htmlUrl)) onOpenUpdatePage()
        },
        onIgnore = { version ->
            release = null
            scope.launch { deps.settings.setIgnoredUpdateVersion(version) }
        },
        onCancelDownload = { deps.updateDownloader.cancel() },
    )
}
