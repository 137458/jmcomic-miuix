package com.jm3.app.miuix.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.download.DownloadTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
     * Android 15+：dataSync 前台服务累计运行 6 小时后系统回调（API 35 单参，
     * API 36 起改为双参）。按时限要求把在途任务置 PAUSED 并退出前台，
     * 用户下次进入应用可手动续传，不做任何保活。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    @Deprecated("Android 16 起由 onTimeout(startId, fgsType) 取代")
    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    private fun handleTimeout() {
        runCatching { Jm3Graph.downloadManager.pauseActive() }
        DownloadNotifier.cancel(this)
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
        }.onFailure {
            running = false
        }
    }

    companion object {

        private const val ACTION_START = "com.jm3.app.miuix.download.action.START"

        /** 后台启动前台服务被系统拒绝后的重试间隔。 */
        private const val START_RETRY_INTERVAL_MS = 5_000L

        /** 进程级作用域：必须比任何 Activity / Service 实例活得久。 */
        private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val attached = AtomicBoolean(false)
        private val throttle = DownloadThrottle(DownloadNotifier.MIN_PROGRESS_INTERVAL_MS)

        /** 服务是否真的在前台运行（由服务实例回写）。 */
        @Volatile
        private var running = false

        /** 上次已知的活动任务数，用于去重：静止状态不重复 start/stop。 */
        @Volatile
        private var lastActiveCount = -1

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
            observerScope.launch {
                var previous: Map<String, DownloadTask> = emptyMap()
                Jm3Graph.downloadManager.queue.updates.collect { tasks ->
                    val active = tasks.activeDownloadCount()
                    val countChanged = active != lastActiveCount
                    lastActiveCount = active

                    if (active > 0) {
                        // running 由服务实例回写：未起来时按退避间隔重试（后台启动可能被拒）。
                        if (!running && canAttemptStart()) start(app)
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
            flushJob = observerScope.launch {
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
