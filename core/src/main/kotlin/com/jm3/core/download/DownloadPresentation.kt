package com.jm3.core.download

/** 通知标题的选型：具体文案在 :app 的字符串资源里，选哪一条由本 module 决定。 */
sealed interface DownloadTitle {

    /** 单项下载（R.string.notification_download_title）。 */
    data object Single : DownloadTitle

    /** 多项下载，[count] 为活动任务数（R.string.notification_download_title_multi）。 */
    data class Multi(val count: Int) : DownloadTitle
}

/** 通知正文的选型。 */
sealed interface DownloadText {

    /** 进度已知：章节名 + 已完成页数 / 总页数（R.string.notification_download_text）。 */
    data class Progress(val chapter: String, val donePages: Int, val total: Int) : DownloadText

    /** 元数据未解析（R.string.notification_download_text_pending）。 */
    data class Pending(val chapter: String) : DownloadText
}

/** 状态栏胶囊极短文本的选型。 */
sealed interface DownloadShortText {

    /** R.string.notification_download_short。 */
    data class Progress(val donePages: Int, val total: Int) : DownloadShortText

    /** R.string.notification_download_short_pending。 */
    data object Pending : DownloadShortText
}

/** 通知进度条的规格；[Determinate] 保证 0 <= donePages <= total。 */
sealed interface DownloadMeter {

    data class Determinate(val donePages: Int, val total: Int) : DownloadMeter

    data object Indeterminate : DownloadMeter
}

/**
 * 一次展示所需的全部事实：展示哪条任务、活动任务数、标题/正文/胶囊/进度条的选型。
 *
 * :app 只把选型映射到字符串资源与 Android 构造器，不再自己判断「该显示什么」。
 */
data class DownloadPresentation(
    val task: DownloadTask,
    val activeCount: Int,
    val title: DownloadTitle,
    val text: DownloadText,
    val shortText: DownloadShortText,
    val meter: DownloadMeter,
)

/**
 * 下载快照 -> 通知展示快照的唯一入口。
 *
 * 只有活动任务（QUEUED / RUNNING）可被展示：前台通知不能把暂停任务画成「正在下载」；
 * 多条活动任务时优先画运行中的那条。列表行（书架、详情）不是这个 module 的调用方，
 * 逐条取标签与动作走 [presentRow]。
 *
 * 返回 null 表示没有可展示的任务，前台服务据此退场。
 */
fun present(tasks: Map<String, DownloadTask>): DownloadPresentation? {
    val task = tasks.values
        .filter { it.status.isActive }
        .minByOrNull { if (it.status == DownloadStatus.RUNNING) 0 else 1 }
        ?: return null
    val activeCount = tasks.activeDownloadCount()
    // 续传快照的 index 可能超过重新解析出的 total（章节被重新分页），展示侧按总量夹取
    val donePages = task.index.coerceIn(0, task.total)
    val total = task.total
    return DownloadPresentation(
        task = task,
        activeCount = activeCount,
        title = if (activeCount > 1) DownloadTitle.Multi(activeCount) else DownloadTitle.Single,
        text = if (total > 0) {
            DownloadText.Progress(task.title, donePages, total)
        } else {
            DownloadText.Pending(task.title)
        },
        shortText = if (total > 0) {
            DownloadShortText.Progress(donePages, total)
        } else {
            DownloadShortText.Pending
        },
        meter = if (total > 0) {
            DownloadMeter.Determinate(donePages, total)
        } else {
            DownloadMeter.Indeterminate
        },
    )
}
