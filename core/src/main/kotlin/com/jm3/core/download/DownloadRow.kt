package com.jm3.core.download

/** 行级状态标签：唯一真源是 [DownloadStatus.label]，:app 只把标签映射成字符串资源。 */
sealed interface DownloadLabel {
    data object Queued : DownloadLabel
    data object Running : DownloadLabel
    data object Paused : DownloadLabel
    data object Done : DownloadLabel
    data object Failed : DownloadLabel
}

/** 列表行的主操作：书架/详情行只渲染这一个按钮，具体图标与文案由 :app 映射。 */
enum class DownloadAction { Open, Pause, Resume, Retry }

/** 单条下载记录的展示选型（书架、详情行共用）。 */
data class DownloadRow(
    val label: DownloadLabel,
    val donePages: Int,
    val total: Int,
    val showsProgressBar: Boolean,
    val action: DownloadAction,
)

/** 状态 -> 展示标签的唯一映射。 */
fun DownloadStatus.label(): DownloadLabel = when (this) {
    DownloadStatus.QUEUED -> DownloadLabel.Queued
    DownloadStatus.RUNNING -> DownloadLabel.Running
    DownloadStatus.PAUSED -> DownloadLabel.Paused
    DownloadStatus.DONE -> DownloadLabel.Done
    DownloadStatus.FAILED -> DownloadLabel.Failed
}

/**
 * 单条记录的展示选型：与 [present]（从整份快照里挑一条画通知）互补，
 * 本函数逐条给出「标签 + 进度条口径 + 主操作」，列表行照此渲染即可。
 */
fun presentRow(record: DownloadRecord): DownloadRow = DownloadRow(
    label = record.status.label(),
    // 续传后的 donePages 可能大于重新解析出的 total，渲染前按总量夹取
    donePages = record.donePages.coerceIn(0, record.total),
    total = record.total,
    // 已完成的行不再画进度条；total 为 0 表示元数据未解析，也无从画起
    showsProgressBar = record.total > 0 && record.status != DownloadStatus.DONE,
    action = when (record.status) {
        DownloadStatus.DONE -> DownloadAction.Open
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> DownloadAction.Pause
        DownloadStatus.PAUSED -> DownloadAction.Resume
        DownloadStatus.FAILED -> DownloadAction.Retry
    },
)
