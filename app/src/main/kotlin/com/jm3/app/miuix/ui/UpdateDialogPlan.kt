package com.jm3.app.miuix.ui

import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.update.ReleaseInfo
import com.jm3.core.update.UpdateProgress

/** 折叠态只展示前几行，展开后整段说明放进弹窗自己的滚动区。 */
internal const val UPDATE_NOTES_PREVIEW_LINES = 12

/**
 * 弹窗的一次快照：Miuix 组件只按这份数据铺像素，不自己判断版本或下载态。
 *
 * 只留决策所需的三类信息：版本跃迁（含日期）、更新说明、下一步动作。体积、安装包文件名、
 * 附件数量、说明行数这类信息对"是否更新"没有影响，不进入计划，也就不可能再堆回界面。
 */
internal data class UpdateDialogPlan(
    val summary: String,
    val primaryText: String,
    val showProgress: Boolean,
    val progressFraction: Float?,
    val progressDetail: String?,
    val showCancelDownload: Boolean,
    val showIgnore: Boolean,
    val hasNotes: Boolean,
)

internal data class UpdateDialogNotesState(val visibleMarkdown: String, val hiddenLineCount: Int)

/**
 * 顶部唯一的一行版本信息：从哪个版本到哪个版本，附发布日期。
 *
 * 原先的"当前版本 / 最新版本 / 版本名称 / 发布日期"四行卡正好把这句重复了四遍，
 * 合并后用户一眼就能看到版本跃迁，日期作为次要信息跟在后面。
 */
internal fun updateDialogSummary(currentVersion: String, release: ReleaseInfo): String {
    val base = "当前 $currentVersion → 最新 ${release.version}"
    return if (release.publishedDate.isNotBlank()) "$base · ${release.publishedDate}" else base
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

/**
 * 下载进度文案：页面下载卡与弹窗主按钮共用这一条规则。
 *
 * 百分比先夹紧到 0~1 再向下取整，服务器给的总长与实际字节差一点时 99.6% 不会显示成 100%，
 * 越界数据夹到 100%；总长未知时进度恒为 0，报 0% 而不是乱跳。
 * [withSize] 为 true 时前置体积（总长未知退回已下字节），供页面那行说明使用；弹窗按钮那一格
 * 只放百分比，体积明细已经在下一行。
 */
internal fun updateProgressText(state: UpdateDownloadState.Downloading, withSize: Boolean = false): String {
    val percent = UpdateProgress.percent(state.progress)
    if (!withSize) return "$percent%"
    val size = formatUpdateSize(if (state.total > 0L) state.total else state.downloaded)
    return "$size（$percent%）"
}

/**
 * 底部动作只有主次两级：主按钮负责"马上下载"，次按钮是"取消下载（仅下载中）"与"稍后"。
 * 「忽略此版本」降级为说明下方的一枚文字链接，不再与主操作并列；复制说明、浏览器看全文、
 * 打开更新页这类入口直接去掉——它们在更新页与 Releases 页面都还能找到，弹窗里只留决策路径。
 */
internal fun buildUpdateDialogPlan(
    currentVersion: String,
    release: ReleaseInfo,
    downloadState: UpdateDownloadState,
    canIgnore: Boolean,
    canCancelDownload: Boolean,
): UpdateDialogPlan {
    val downloading = downloadState as? UpdateDownloadState.Downloading
    return UpdateDialogPlan(
        summary = updateDialogSummary(currentVersion, release),
        primaryText = updateDialogPrimaryText(hasApk = release.apk != null, downloadState = downloadState),
        showProgress = downloading != null,
        progressFraction = downloading?.let(::updateDialogProgressFraction),
        progressDetail = downloading?.let(::updateDialogDownloadDetail),
        showCancelDownload = downloading != null && canCancelDownload,
        showIgnore = canIgnore && downloading == null,
        hasNotes = release.notes.isNotBlank(),
    )
}
