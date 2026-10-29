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
 * Android 16.1+ 才走提升型持续通知（状态栏胶囊，[Notification.Builder.setRequestPromotedOngoing] +
 * [Notification.Builder.setShortCriticalText]）；16.0 及更低版本走 NotificationCompat 进度条形态。
 */
internal object DownloadNotifier {

    const val CHANNEL_DOWNLOADS = "downloads"

    /** 前台服务通知 ID；与 [CHANNEL_DOWNLOADS] 一一对应，全应用仅此一条下载通知。 */
    const val NOTIFICATION_ID = 1001

    /** 暂停提示的独立 ID：前台通知被服务退出移除后，这条提示仍要留在通知栏。 */
    const val NOTIFICATION_ID_PAUSED = 1002

    /** 进度刷新最小间隔（毫秒）。完成/失败/活动任务数变化不受此限制。 */
    const val MIN_PROGRESS_INTERVAL_MS = 300L

    /** 渠道是否已在本进程内创建：进度刷新时 build 每秒可被调多次，重复建渠道要走 Binder。 */
    @Volatile
    private var channelReady = false

    /** 通知渠道：幂等创建，IMPORTANCE_LOW 保证无声音、不弹横幅。 */
    fun ensureChannel(context: Context) {
        if (channelReady) return
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
        channelReady = true
    }

    /**
     * 用户关闭通知权限/渠道时不影响下载：前台服务照常运行，仅跳过可见通知的发布。
     */
    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** 撤掉上一次超时留下的暂停提示：新的下载开始时旧提示会误导用户。 */
    fun cancelPaused(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PAUSED)
    }

    /**
     * 一次性「已暂停」提示：前台服务退场时前台通知会随之消失，只静默停服会让用户以为下载已完成。
     * 同渠道（IMPORTANCE_LOW，无声音）、非前台、可点击回主界面，点击后自动消失。
     */
    fun notifyPaused(context: Context, reason: CharSequence) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notification_download_paused_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PAUSED, notification)
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
            .setShortCriticalText(shortText(context, task))
        // 只有 16.1 及以上才有 setRequestPromotedOngoing：16.0 设备上直接调用会 NoSuchMethodError，
        // 而本方法由 onStartCommand 直调、异常没人接，会把前台服务整体带崩。
        if (supportsPromotedOngoing()) {
            builder.setRequestPromotedOngoing(true)
        }
        if (task.total > 0) {
            builder.setProgress(task.total, task.index, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    /**
     * 提升型持续通知只从 Android 16.1（API 36.1）起可用：setRequestPromotedOngoing 是 36.1 新增，
     * 而模拟器/真机上的 16.0 只有 setShortCriticalText（36.0）。
     * SDK_INT_FULL 本身是 36 新增的静态字段，必须先判 SDK_INT >= 36 短路，否则低版本会 NoSuchFieldError。
     */
    @RequiresApi(36)
    private fun supportsPromotedOngoing(): Boolean =
        Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1

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
            // 不给 IMMEDIATE 时 Android 12+ 会把首条前台通知压后约 10 秒，进度一开始是空白。
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(
                if (indeterminate) 0 else task.total,
                if (indeterminate) 0 else task.index,
                indeterminate,
            )
            .build()
    }

    /**
     * 胶囊上的极短文本：只放「3/12」这类数字，进度未知时用固定的「下载中」。
     * 胶囊宽度只够几个字符，塞章节标题会被系统拦腰截断。
     */
    @RequiresApi(36)
    private fun shortText(context: Context, task: DownloadTask): String =
        if (task.total > 0) {
            context.getString(R.string.notification_download_short, task.index, task.total)
        } else {
            context.getString(R.string.notification_download_short_pending)
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
