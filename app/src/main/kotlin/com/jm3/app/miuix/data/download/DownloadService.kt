package com.jm3.app.miuix.data.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.R
import com.jm3.core.download.DownloadPresentation
import com.jm3.core.download.DownloadTask
import com.jm3.core.download.ForegroundAction
import com.jm3.core.download.ForegroundPolicy
import com.jm3.core.download.present
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 下载前台服务（dataSync）。
 *
 * 设计要点：
 * - 不对外暴露 Binder：队列本身是进程内单例（[Jm3Graph.downloads]），服务只负责
 *   「持有前台状态 + 展示通知」，不承担调度，因此进程被杀后任务状态由
 *   [DownloadManager.ensureStarted] 的台账恢复兜底，而不是靠服务重启。
 * - 服务实例不做观察：观察者挂在进程级作用域（[attach]），这样界面退到后台、
 *   Activity 被回收之后，服务仍能随队列状态自动开关，也不会因为多个 Activity
 *   重复注册而重复 startForegroundService。
 * - 该做什么由 :core 的 [ForegroundPolicy] 决定（活动任务数 → 拉起/停止/发进度/补发），
 *   这里只执行动作，并把「服务是否真的在前台」回写给它；拉起被拒后的补评估由 [ForegroundRunner] 排期。
 * - 画什么由 :core 的 [present] 决定，[DownloadNotifier] 只把选型翻成通知内容。
 */
class DownloadService : Service() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appContext = applicationContext
        val presentation = present(currentTasks())
        if (presentation == null) {
            // 竞态：startForegroundService 之后任务已清空/已完成。
            // Android 12+（minSdk 31）仍要求在 stopSelf 前先调用 startForeground，否则会抛出 ForegroundServiceDidNotStartInTimeException。
            startForegroundSafely(DownloadNotifier.buildStopping(this))
            coordinator.stopAndCancel()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (startForegroundSafely(DownloadNotifier.build(this, presentation))) {
            synchronized(policyLock) {
                policy.onServiceForeground()
            }
            // 新下载已经开跑，上一次超时留下的「已暂停」提示已经过期，撤掉避免误导。
            DownloadNotifier.cancelPaused(this)
        }
        // 进程被杀后不自动重建：任务由落库记录恢复，避免死循环拉起空服务。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        appContext = applicationContext
        coordinator.stopAndCancel()
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
        appContext = applicationContext
        runCatching { Jm3Graph.downloads.manager.pauseActive() }
            .onFailure { e ->
                // 暂停失败也要继续退场：先把失败留痕，否则通知消失了却没人知道任务没停。
                Log.w(TAG, "超时暂停在途任务失败", e)
            }
        // 在 policyLock 内原子置停、取消 flushJob 并撤销通知，防止在途补发把 ongoing 通知重新投递回通知栏。
        coordinator.stopAndCancel()
        // 前台通知会随退场一起消失：不留一条可见提示，用户只会看到通知无声消失、以为下载完成。
        DownloadNotifier.notifyPaused(this, getString(R.string.notification_download_paused_text))
        stopSelf()
    }

    /** targetSdk 34+ 未声明 FOREGROUND_SERVICE_DATA_SYNC 时会抛异常，失败也不让下载崩掉。 */
    private fun startForegroundSafely(notification: Notification): Boolean {
        // minSdk 31（>= Q），可直接使用 FOREGROUND_SERVICE_TYPE_DATA_SYNC
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        return runCatching {
            ServiceCompat.startForeground(this, DownloadNotifier.NOTIFICATION_ID, notification, type)
        }.onFailure { e ->
            // 服务已 started 却没进入前台，5 秒后系统会抛 RemoteServiceException，必须主动结束。
            Log.w(TAG, "startForeground 失败", e)
            synchronized(policyLock) {
                policy.onServiceStopped()
            }
            stopSelf()
        }.isSuccess
    }

    companion object {

        private const val TAG = "DownloadService"

        private const val ACTION_START = "com.jm3.app.miuix.download.action.START"

        private val attached = AtomicBoolean(false)

        /** 同一批任务内的拉起重试窗口：策略的退避判定与这里的到点补评估共用同一个值。 */
        private const val START_RETRY_INTERVAL_MS = ForegroundPolicy.DEFAULT_START_RETRY_INTERVAL_MS

        /** 前台策略与 flushJob/通知投递共享同一把锁，保证主线程生命周期回调与后台队列协程串行访问。 */
        private val policyLock = Any()

        /** 前台服务的全部决策状态：进程内一份，随 Activity 重建而保持。 */
        private val policy = ForegroundPolicy(startRetryIntervalMs = START_RETRY_INTERVAL_MS)

        @Volatile
        private var appContext: Context? = null

        private val coordinator: ForegroundNotificationCoordinator by lazy {
            ForegroundNotificationCoordinator(
                policy = policy,
                lock = policyLock,
                scope = Jm3Graph.appScope,
                latestTasks = ::currentTasks,
                postNotification = { presentation ->
                    val app = appContext ?: return@ForegroundNotificationCoordinator
                    if (!DownloadNotifier.canPostNotifications(app)) return@ForegroundNotificationCoordinator
                    try {
                        NotificationManagerCompat.from(app)
                            .notify(DownloadNotifier.NOTIFICATION_ID, DownloadNotifier.build(app, presentation))
                    } catch (e: SecurityException) {
                        // 权限在检查与投递之间被撤销：跳过本次进度刷新，服务继续运行
                        Log.w(TAG, "进度通知投递被拒", e)
                    }
                },
                cancelNotification = {
                    appContext?.let(DownloadNotifier::cancel)
                },
            )
        }

        /**
         * 注册「活动任务数 → 前台服务生命周期」观察者，进程内调用一次即可
         * （建议在 MainActivity.onCreate 里、Jm3Graph.init 之后调用）。
         */
        fun attach(context: Context) {
            if (!attached.compareAndSet(false, true)) return
            val app = context.applicationContext
            appContext = app
            // 队列更新只是快照来源：决策与执行都交给 runner，它自己负责拉起被拒后的到点补评估。
            val runner = ForegroundRunner(
                policy = policy,
                scope = Jm3Graph.appScope,
                retryIntervalMs = START_RETRY_INTERVAL_MS,
                latestTasks = { currentTasks() },
                perform = { action -> perform(app, action) },
                lock = policyLock,
            )
            // 全应用只允许一处长期协程作用域：appScope 随进程存活，attach 的幂等由 attached 保证。
            Jm3Graph.appScope.launch {
                Jm3Graph.downloads.manager.queue.updates.collect { tasks -> runner.onTasks(tasks) }
            }
        }

        /** 拉起前台服务；被系统拒绝时返回 false，由 [ForegroundRunner] 排一次到点补评估。 */
        fun start(context: Context): Boolean {
            appContext = context.applicationContext
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            // minSdk 31：统一走 startForegroundService，由 onStartCommand 在 5s 内 startForeground。
            return runCatching { context.startForegroundService(intent) }
                .onFailure { e ->
                    // 后台启动被拒（ForegroundServiceStartNotAllowedException）时界面毫无反馈，至少留痕。
                    Log.w(TAG, "启动下载前台服务失败", e)
                    // 回滚活动数快照，让退避窗口结束后的补评估还能再次尝试拉起
                    synchronized(policyLock) {
                        policy.onStartFailed()
                    }
                }
                .isSuccess
        }

        fun stop(context: Context) {
            appContext = context.applicationContext
            coordinator.stopAndCancel()
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }

        /** 活动任务快照：直接读 StateFlow，不引入 Binder。 */
        internal fun currentTasks(): Map<String, DownloadTask> =
            Jm3Graph.downloads.manager.queue.updates.value

        /** 执行一条动作；返回值只对 [ForegroundAction.Start] 有意义（拉起是否被系统接受）。 */
        private fun perform(app: Context, action: ForegroundAction): Boolean {
            appContext = app
            return when (action) {
                ForegroundAction.Start -> start(app)
                ForegroundAction.Stop -> {
                    stop(app)
                    true
                }
                is ForegroundAction.Publish -> {
                    coordinator.publish(action.tasks)
                    true
                }
                is ForegroundAction.ScheduleFlush -> {
                    coordinator.scheduleFlush(action.delayMs)
                    true
                }
            }
        }
    }
}

/**
 * 前台通知节流补发与停服撤销协调器：把「检查运行态 + 投递通知 / 停止 + 取消补发 + 撤通知」
 * 收敛到同一把 [lock] 内，杜绝停服或 6 小时超时后补发协程将 ongoing 通知（ID 1001）重新投递到通知栏。
 */
internal class ForegroundNotificationCoordinator(
    private val policy: ForegroundPolicy,
    private val lock: Any,
    private val scope: CoroutineScope,
    private val latestTasks: () -> Map<String, DownloadTask>,
    private val postNotification: (DownloadPresentation) -> Unit,
    private val cancelNotification: () -> Unit,
) {
    private var flushJob: Job? = null

    fun scheduleFlush(delayMs: Long) {
        synchronized(lock) {
            if (flushJob?.isActive == true) return
            flushJob = scope.launch {
                delay(delayMs)
                synchronized(lock) {
                    if (!coroutineContext.isActive) return@synchronized
                    flushJob = null
                    val due = policy.onFlushDue(latestTasks())
                    if (due is ForegroundAction.Publish) {
                        publishLocked(due.tasks)
                    }
                }
            }
        }
    }

    fun publish(tasks: Map<String, DownloadTask>) {
        synchronized(lock) {
            publishLocked(tasks)
        }
    }

    fun stopAndCancel() {
        synchronized(lock) {
            policy.onServiceStopped()
            flushJob?.cancel()
            flushJob = null
            cancelNotification()
        }
    }

    private fun publishLocked(tasks: Map<String, DownloadTask>) {
        if (!policy.isRunning) return
        val presentation = present(tasks) ?: return
        postNotification(presentation)
    }
}
