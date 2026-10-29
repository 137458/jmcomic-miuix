package com.jm3.app.miuix.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jm3.app.miuix.MainActivity
import com.jm3.app.miuix.R
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.DownloadTask

/**
 * 下载通知：单渠道（IMPORTANCE_LOW，无声，不出横幅）。
 *
 * Android 16+ 走提升型持续通知（状态栏胶囊，[Notification.Builder.setRequestPromotedOngoing] +
 * [Notification.Builder.setShortCriticalText]）；低版本走 NotificationCompat 进度条形态。
 */
internal object DownloadNotifier {

    const val CHANNEL_DOWNLOADS = "downloads"

    /** 前台服务通知 ID；与 [CHANNEL_DOWNLOADS] 一一对应，全应用仅此一条下载通知。 */
    const val NOTIFICATION_ID = 1001

    /** 进度刷新最小间隔（毫秒）。完成/失败/活动任务数变化不受此限制。 */
    const val MIN_PROGRESS_INTERVAL_MS = 300L

    /** 通知渠道：幂等创建，IMPORTANCE_LOW 保证无声音、不弹横幅。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            context.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_downloads_desc)
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 用户关闭通知权限/渠道时不影响下载：前台服务照常运行，仅跳过可见通知的发布。
     */
    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * 前台服务通知内容：优先展示 RUNNING 任务，没有则展示队首 QUEUED 任务
     * （并发上限为 2，聚合展示会让进度条来回跳，因此只跟一个任务）。
     * 没有可展示任务时返回 null，调用方据此退出服务。
     */
    fun build(context: Context, tasks: Map<String, DownloadTask>): Notification? {
        val task = pickForegroundTask(tasks) ?: return null
        ensureChannel(context)

        val active = tasks.activeDownloadCount()
        val title = if (active > 1) {
            context.getString(R.string.notification_download_title_multi, active)
        } else {
            context.getString(R.string.notification_download_title)
        }
        val text = if (task.total > 0) {
            context.getString(R.string.notification_download_text, task.title, task.index, task.total)
        } else {
            context.getString(R.string.notification_download_text_pending, task.title)
        }

        return if (Build.VERSION.SDK_INT >= 36) {
            buildPromoted(context, task, title, text, contentIntent(context))
        } else {
            buildCompat(context, task, title, text, contentIntent(context))
        }
    }

    /** 幂等：同 ID 覆盖更新，点击回到主界面。 */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Android 16+：提升型持续通知，手机上折叠成状态栏胶囊。 */
    @RequiresApi(36)
    private fun buildPromoted(
        context: Context,
        task: DownloadTask,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(shortText(context, task))
        if (task.total > 0) {
            builder.setProgress(task.total, task.index, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    /** Android 8~15：NotificationCompat 进度形态（indeterminate 表示 total 尚未解析出来）。 */
    private fun buildCompat(
        context: Context,
        task: DownloadTask,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ): Notification {
        val indeterminate = task.total <= 0
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setProgress(
                if (indeterminate) 0 else task.total,
                if (indeterminate) 0 else task.index,
                indeterminate,
            )
            .build()
    }

    /** 胶囊上的极短文本：进度未知时退回章节标题（setShortCriticalText 只接受 String）。 */
    @RequiresApi(36)
    private fun shortText(context: Context, task: DownloadTask): String =
        if (task.total > 0) {
            context.getString(R.string.notification_download_short, task.index, task.total)
        } else {
            task.title
        }

    /** 优先 RUNNING，其次队首 QUEUED。 */
    internal fun pickForegroundTask(tasks: Map<String, DownloadTask>): DownloadTask? =
        tasks.values.firstOrNull { it.status == DownloadStatus.RUNNING }
            ?: tasks.values.firstOrNull { it.status == DownloadStatus.QUEUED }
}

/**
 * 进度节流：[minIntervalMs] 内的重复进度丢弃，避免每页一次 notify 打爆通知服务。
 * 纯逻辑、无 Android 依赖，可直接单测。
 */
internal class DownloadThrottle(private val minIntervalMs: Long = 300L) {

    private var lastEmitAt = NEVER

    /** [immediate] 为 true（完成/失败/活动数变化）时无条件放行并重置窗口。 */
    fun shouldEmit(nowMs: Long, immediate: Boolean = false): Boolean {
        if (immediate) {
            lastEmitAt = nowMs
            return true
        }
        if (lastEmitAt == NEVER || nowMs - lastEmitAt >= minIntervalMs) {
            lastEmitAt = nowMs
            return true
        }
        return false
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
