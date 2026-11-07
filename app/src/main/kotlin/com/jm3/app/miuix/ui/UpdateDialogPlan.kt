package com.jm3.app.miuix.ui

import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateProgress

/** 折叠态只展示前几行，展开后整段说明放进弹窗自己的滚动区。 */
internal const val UPDATE_NOTES_PREVIEW_LINES = 12

internal data class UpdateDialogInfoRow(val label: String, val value: String)

internal enum class UpdateDialogAction {
    CANCEL_DOWNLOAD, IGNORE_VERSION, COPY_NOTES, OPEN_RELEASE, OPEN_UPDATE_PAGE, DISMISS,
}

internal data class UpdateDialogActionItem(val action: UpdateDialogAction, val enabled: Boolean = true)

/** 弹窗的一次快照：Miuix 组件只按这份数据铺像素，不自己判断版本或下载态。 */
internal data class UpdateDialogPlan(
    val infoRows: List<UpdateDialogInfoRow>,
    val primaryText: String,
    val showProgress: Boolean,
    val progressFraction: Float?,
    val progressDetail: String?,
    val actions: List<UpdateDialogActionItem>,
    val hasNotes: Boolean,
)

internal data class UpdateDialogNotesState(val visibleMarkdown: String, val hiddenLineCount: Int)

internal fun updateDialogActionLabel(action: UpdateDialogAction, noteCopied: Boolean): String = when (action) {
    UpdateDialogAction.CANCEL_DOWNLOAD -> "取消下载"
    UpdateDialogAction.IGNORE_VERSION -> "忽略此版本"
    UpdateDialogAction.COPY_NOTES -> if (noteCopied) "已复制说明" else "复制更新说明"
    UpdateDialogAction.OPEN_RELEASE -> "在浏览器查看完整说明"
    UpdateDialogAction.OPEN_UPDATE_PAGE -> "打开更新页面"
    UpdateDialogAction.DISMISS -> "稍后"
}

/** 版本信息逐行列出：缺安装包也保留占位行，避免长短版本之间行高跳变。 */
internal fun updateDialogInfoRows(currentVersion: String, release: ReleaseInfo): List<UpdateDialogInfoRow> {
    val apk = release.apk
    return listOf(
        UpdateDialogInfoRow("当前版本", currentVersion),
        UpdateDialogInfoRow("最新版本", release.version),
        UpdateDialogInfoRow("版本名称", release.name),
        UpdateDialogInfoRow("发布日期", release.publishedDate),
        UpdateDialogInfoRow("安装包", apk?.name ?: "未附带 APK"),
        UpdateDialogInfoRow("安装包大小", apk?.let { formatUpdateSize(it.size) } ?: "未知"),
        UpdateDialogInfoRow("附件数量", release.assets.size.toString()),
        UpdateDialogInfoRow("更新说明", "${release.notes.lines().size} 行"),
    )
}

/** 折叠态取前 N 行；空白说明与短说明都不显示「展开全部」。 */
internal fun updateDialogNotesState(markdown: String, expanded: Boolean, previewLines: Int): UpdateDialogNotesState {
    if (markdown.isBlank() || expanded) return UpdateDialogNotesState(markdown, 0)
    val lines = markdown.lines()
    val limit = previewLines.coerceAtLeast(1)
    return UpdateDialogNotesState(
        visibleMarkdown = lines.take(limit).joinToString("\n"),
        hiddenLineCount = (lines.size - limit).coerceAtLeast(0),
    )
}

/** total<=0 表示分块传输，进度条退回不确定态而不是一直钉在 0%。 */
internal fun updateDialogProgressFraction(state: UpdateDownloadState.Downloading): Float? =
    if (state.total > 0L) UpdateProgress.fraction(state.downloaded, state.total) else null

internal fun updateDialogDownloadDetail(state: UpdateDownloadState.Downloading): String {
    val downloaded = formatUpdateSize(state.downloaded)
    val speed = formatUpdateSpeed(state.speedBytesPerSec)
    return if (state.total > 0L) {
        "$downloaded / ${formatUpdateSize(state.total)} · $speed"
    } else {
        "$downloaded · $speed · 总长未知"
    }
}

/** 有回调才给对应操作；下载中把「忽略」禁掉，同时补上「取消下载」。 */
internal fun buildUpdateDialogPlan(
    currentVersion: String,
    release: ReleaseInfo,
    downloadState: UpdateDownloadState,
    canIgnore: Boolean,
    canCancelDownload: Boolean,
    canOpenUpdatePage: Boolean,
): UpdateDialogPlan {
    val downloading = downloadState as? UpdateDownloadState.Downloading
    val hasNotes = release.notes.isNotBlank()
    val actions = buildList {
        if (downloading != null && canCancelDownload) add(UpdateDialogActionItem(UpdateDialogAction.CANCEL_DOWNLOAD))
        if (canIgnore) {
            add(UpdateDialogActionItem(UpdateDialogAction.IGNORE_VERSION, enabled = downloading == null))
        }
        if (hasNotes) add(UpdateDialogActionItem(UpdateDialogAction.COPY_NOTES))
        add(UpdateDialogActionItem(UpdateDialogAction.OPEN_RELEASE))
        if (canOpenUpdatePage) add(UpdateDialogActionItem(UpdateDialogAction.OPEN_UPDATE_PAGE))
        add(UpdateDialogActionItem(UpdateDialogAction.DISMISS))
    }
    return UpdateDialogPlan(
        infoRows = updateDialogInfoRows(currentVersion, release),
        primaryText = updateDialogPrimaryText(hasApk = release.apk != null, downloadState = downloadState),
        showProgress = downloading != null,
        progressFraction = downloading?.let(::updateDialogProgressFraction),
        progressDetail = downloading?.let(::updateDialogDownloadDetail),
        actions = actions,
        hasNotes = hasNotes,
    )
}
