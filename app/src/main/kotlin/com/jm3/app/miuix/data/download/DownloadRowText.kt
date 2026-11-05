package com.jm3.app.miuix.data.download

import androidx.annotation.StringRes
import com.jm3.app.miuix.R
import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadLabel

/**
 * 行级选型 -> 字符串资源：:core 只给 [DownloadLabel] / [DownloadAction]，
 * 具体文案归 :app。同一条资源只能被一个选型使用，否则书架会把两种状态显示成同一句话。
 */
@StringRes
fun DownloadLabel.textRes(): Int = when (this) {
    DownloadLabel.Queued -> R.string.download_status_queued
    DownloadLabel.Running -> R.string.download_status_running
    DownloadLabel.Paused -> R.string.download_status_paused
    DownloadLabel.Done -> R.string.download_status_done
    DownloadLabel.Failed -> R.string.download_status_failed
}

/** 主操作的图标 contentDescription。 */
@StringRes
fun DownloadAction.contentDescriptionRes(): Int = when (this) {
    DownloadAction.Open -> R.string.download_action_open
    DownloadAction.Pause -> R.string.download_action_pause
    DownloadAction.Resume -> R.string.download_action_resume
    DownloadAction.Retry -> R.string.download_action_retry
}
