package com.jm3.app.miuix.data.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jm3.app.miuix.MainActivity
import com.jm3.app.miuix.R
import com.jm3.core.download.DownloadMeter
import com.jm3.core.download.DownloadPresentation
import com.jm3.core.download.DownloadShortText
import com.jm3.core.download.DownloadText
import com.jm3.core.download.DownloadTitle

/**
 * 下载通知：单渠道（IMPORTANCE_LOW，无声，不出横幅）。
 *
 * 「显示什么」由 :core 的 [com.jm3.core.download.present] 决定（[DownloadPresentation]），
 * 本对象只做两件事：选型 -> 字符串资源，选型 -> Android 通知构造器。
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
     * Android 13+ 需显式校验 POST_NOTIFICATIONS；areNotificationsEnabled 只反映渠道总开关，
     * 不覆盖运行时权限被撤销的情形。
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

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
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PAUSED, notification)
        } catch (e: SecurityException) {
            // 权限在检查与投递之间被撤销：放弃这条提示，不影响下载流程
            Log.w("DownloadNotifier", "暂停提示投递被拒", e)
        }
    }

    /**
     * 前台服务通知：内容全部来自 [presentation]，本方法不再参与「展示哪条任务」的判定。
     */
    fun build(context: Context, presentation: DownloadPresentation): Notification {
        ensureChannel(context)
        val content = content(context, presentation)
        val intent = contentIntent(context)
        return if (Build.VERSION.SDK_INT >= 36) {
            buildPromoted(context, content, intent)
        } else {
            buildCompat(context, content, intent)
        }
    }

    /** 选型 -> 字符串资源。两条渲染分支共用这一份文案。 */
    private fun content(context: Context, presentation: DownloadPresentation): NotificationContent {
        val title = when (val spec = presentation.title) {
            DownloadTitle.Single -> context.getString(R.string.notification_download_title)
            is DownloadTitle.Multi -> context.getString(R.string.notification_download_title_multi, spec.count)
        }
        val text = when (val spec = presentation.text) {
            is DownloadText.Progress -> context.getString(
                R.string.notification_download_text,
                spec.chapter,
                spec.donePages,
                spec.total,
            )
            is DownloadText.Pending -> context.getString(R.string.notification_download_text_pending, spec.chapter)
        }
        val shortText = when (val spec = presentation.shortText) {
            is DownloadShortText.Progress -> context.getString(
                R.string.notification_download_short,
                spec.donePages,
                spec.total,
            )
            DownloadShortText.Pending -> context.getString(R.string.notification_download_short_pending)
        }
        return NotificationContent(title, text, shortText, presentation.meter)
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

    /** 一次通知渲染所需的全部内容：两个 Android 构造器都从这里取，避免两条分支各写一遍。 */
    private data class NotificationContent(
        val title: String,
        val text: String,
        val shortText: String,
        val meter: DownloadMeter,
    )

    /** Android 16+：提升型持续通知，手机上折叠成状态栏胶囊。 */
    @RequiresApi(36)
    private fun buildPromoted(
        context: Context,
        content: NotificationContent,
        contentIntent: PendingIntent,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_DOWNLOADS)
            .applyCommon(content, contentIntent)
            .applyMeter(content.meter)
            .setShortCriticalText(content.shortText)
        // 只有 16.1 及以上才有 setRequestPromotedOngoing：16.0 设备上直接调用会 NoSuchMethodError，
        // 而本方法由 onStartCommand 直调、异常没人接，会把前台服务整体带崩。
        if (supportsPromotedOngoing()) {
            builder.setRequestPromotedOngoing(true)
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
        content: NotificationContent,
        contentIntent: PendingIntent,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
        .applyCommon(content, contentIntent)
        .applyMeter(content.meter)
        .build()

    /**
     * 两个构造器共有的字段，只写一次（两处差异只有 silent/priority 与提升型专属字段）。
     */
    @RequiresApi(36)
    private fun Notification.Builder.applyCommon(
        content: NotificationContent,
        contentIntent: PendingIntent,
    ): Notification.Builder = apply {
        setSmallIcon(android.R.drawable.stat_sys_download)
        setContentTitle(content.title)
        setContentText(content.text)
        setOngoing(true)
        setOnlyAlertOnce(true)
        setShowWhen(false)
        setCategory(Notification.CATEGORY_PROGRESS)
        setVisibility(Notification.VISIBILITY_PUBLIC)
        setContentIntent(contentIntent)
        setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun NotificationCompat.Builder.applyCommon(
        content: NotificationContent,
        contentIntent: PendingIntent,
    ): NotificationCompat.Builder = apply {
        setSmallIcon(android.R.drawable.stat_sys_download)
        setContentTitle(content.title)
        setContentText(content.text)
        setOngoing(true)
        setSilent(true)
        setOnlyAlertOnce(true)
        setShowWhen(false)
        setCategory(NotificationCompat.CATEGORY_PROGRESS)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        setPriority(NotificationCompat.PRIORITY_LOW)
        setContentIntent(contentIntent)
        // 不给 IMMEDIATE 时 Android 12+ 会把首条前台通知压后约 10 秒，进度一开始是空白。
        setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    @RequiresApi(36)
    private fun Notification.Builder.applyMeter(meter: DownloadMeter): Notification.Builder = apply {
        when (meter) {
            is DownloadMeter.Determinate -> setProgress(meter.total, meter.donePages, false)
            DownloadMeter.Indeterminate -> setProgress(0, 0, true)
        }
    }

    private fun NotificationCompat.Builder.applyMeter(meter: DownloadMeter): NotificationCompat.Builder = apply {
        when (meter) {
            is DownloadMeter.Determinate -> setProgress(meter.total, meter.donePages, false)
            DownloadMeter.Indeterminate -> setProgress(0, 0, true)
        }
    }
}
