package com.jm3.app.miuix.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.R
import com.jm3.core.download.DownloadTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 下载前台服务（dataSync）。
 *
 * 设计要点：
 * - 不对外暴露 Binder：队列本身是进程内单例（[Jm3Graph.downloadManager]），服务只负责
 *   「持有前台状态 + 展示通知」，不承担调度，因此进程被杀后任务状态由
 *   [DownloadManager.recoverInterruptedRecords] 兜底，而不是靠服务重启。
 * - 服务实例不做观察：观察者挂在进程级作用域（[attach]），这样界面退到后台、
 *   Activity 被回收之后，服务仍能随队列状态自动开关，也不会因为多个 Activity
 *   重复注册而重复 startForegroundService。
 * - 生命周期唯一驱动量是「活动任务数」（QUEUED + RUNNING），由
 *   [Map.activeDownloadCount] 计算，PAUSED/DONE/FAILED 一律不占前台。
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = DownloadNotifier.build(this, currentTasks())
        if (notification == null) {
            // 竞态：startForegroundService 之后任务已清空/已完成，避免超时 ANR 直接退出。
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        startForegroundSafely(notification)
        // 新下载已经开跑，上一次超时留下的「已暂停」提示已经过期，撤掉避免误导。
        DownloadNotifier.cancelPaused(this)
        // 进程被杀后不自动重建：任务由落库记录恢复，避免死循环拉起空服务。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        flushJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Android 15+：dataSync 前台服务累计运行 6 小时后系统回调（双参 onTimeout 是 API 35 新增，
     * 单参 onTimeout 是 API 34 新增）。按时限要求把在途任务置 PAUSED 并退出前台，
     * 用户下次进入应用可手动续传，不做任何保活。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    @Deprecated("Android 15 起由 onTimeout(startId, fgsType) 取代")
    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        runCatching { Jm3Graph.downloadManager.pauseActive() }
        DownloadNotifier.cancel(this)
        // 前台通知会随退场一起消失：不留一条可见提示，用户只会看到通知无声消失、以为下载完成。
        DownloadNotifier.notifyPaused(this, getString(R.string.notification_download_paused_text))
        running = false
        stopSelf()
    }

    /** targetSdk 34+ 未声明 FOREGROUND_SERVICE_DATA_SYNC 时会抛异常，失败也不让下载崩掉。 */
    private fun startForegroundSafely(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, DownloadNotifier.NOTIFICATION_ID, notification, type)
        }.onFailure { e ->
            // 服务已 started 却没进入前台，5 秒后系统会抛 RemoteServiceException，必须主动结束。
            Log.w(TAG, "startForeground 失败", e)
            running = false
            stopSelf()
        }
    }

    companion object {

        private const val TAG = "DownloadService"

        private const val ACTION_START = "com.jm3.app.miuix.download.action.START"

        /** 同一批任务内两次拉起尝试的最小间隔：只压抖动，不构成保活。 */
        private const val START_RETRY_INTERVAL_MS = 5_000L

        private val attached = AtomicBoolean(false)
        private val throttle = DownloadThrottle(DownloadNotifier.MIN_PROGRESS_INTERVAL_MS)

        /** 服务是否真的在前台运行（由服务实例回写）。 */
        @Volatile
        private var running = false

        /** 上次已知的活动任务数，用于去重：静止状态不重复 start/stop。 */
        @Volatile
        private var lastActiveCount = -1

        /** 上次拉起尝试的时间戳：与 [START_RETRY_INTERVAL_MS] 配对，只用于压同一次任务数变化内的抖动。 */
        private var lastStartAttemptAt = 0L

        /** 节流窗口末尾的补发任务：保证进度条最终能停在真实值上。 */
        private var flushJob: Job? = null

        /**
         * 注册「活动任务数 → 前台服务生命周期」观察者，进程内调用一次即可
         * （建议在 MainActivity.onCreate 里、Jm3Graph.init 之后调用）。
         */
        fun attach(context: Context) {
            if (!attached.compareAndSet(false, true)) return
            val app = context.applicationContext
            // 全应用只允许一处长期协程作用域：appScope 随进程存活，attach 的幂等由 attached 保证。
            Jm3Graph.appScope.launch {
                var previous: Map<String, DownloadTask> = emptyMap()
                Jm3Graph.downloadManager.queue.updates.collect { tasks ->
                    val active = tasks.activeDownloadCount()
                    // 首次 attach 时 lastActiveCount 为 -1，本身就算一次变化。
                    val countChanged = active != lastActiveCount
                    lastActiveCount = active

                    if (active > 0) {
                        // 只在活动任务数变化时尝试拉起：集合不变还周期性重试就成了保活，
                        // 违背「生命周期唯一驱动量是活动任务数」。
                        // canAttemptStart 的间隔只用于压住同一次变化内的抖动（后台启动可能被拒）。
                        if (!running && countChanged && canAttemptStart()) start(app)
                        if (running) {
                            val immediate = countChanged || tasks.hasTerminalTransitionFrom(previous)
                            if (throttle.shouldEmit(System.currentTimeMillis(), immediate)) {
                                publish(app, tasks)
                            } else {
                                scheduleFlush(app)
                            }
                        }
                    } else if (running) {
                        stop(app)
                    }
                    previous = tasks
                }
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            // minSdk 26：统一走 startForegroundService，由 onStartCommand 在 5s 内 startForeground。
            runCatching { context.startForegroundService(intent) }
                .onFailure { e ->
                    // 后台启动被拒（ForegroundServiceStartNotAllowedException）时界面毫无反馈，至少留痕。
                    Log.w(TAG, "启动下载前台服务失败", e)
                }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }

        /** 活动任务快照：直接读 StateFlow，不引入 Binder。 */
        internal fun currentTasks(): Map<String, DownloadTask> =
            Jm3Graph.downloadManager.queue.updates.value

        private fun canAttemptStart(): Boolean {
            val now = System.currentTimeMillis()
            if (lastStartAttemptAt != 0L && now - lastStartAttemptAt < START_RETRY_INTERVAL_MS) {
                return false
            }
            lastStartAttemptAt = now
            return true
        }

        private fun scheduleFlush(app: Context) {
            if (flushJob?.isActive == true) return
            flushJob = Jm3Graph.appScope.launch {
                delay(DownloadNotifier.MIN_PROGRESS_INTERVAL_MS)
                if (running) publish(app, currentTasks())
            }
        }

        private fun publish(app: Context, tasks: Map<String, DownloadTask>) {
            // 通知被关闭时服务照常运行，只是不再发可见进度。
            if (!DownloadNotifier.canPostNotifications(app)) return
            val notification = DownloadNotifier.build(app, tasks) ?: return
            NotificationManagerCompat.from(app)
                .notify(DownloadNotifier.NOTIFICATION_ID, notification)
        }
    }
}
