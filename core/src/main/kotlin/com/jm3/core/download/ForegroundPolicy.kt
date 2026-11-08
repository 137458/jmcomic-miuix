package com.jm3.core.download

/**
 * 前台服务该做什么：由活动任务数推导出来的一组动作，与 Android 服务生命周期解耦。
 */
sealed interface ForegroundAction {

    /** 拉起前台服务（startForegroundService）。 */
    data object Start : ForegroundAction

    /** 退出前台并结束服务。 */
    data object Stop : ForegroundAction

    /** 立刻把这份任务快照画进通知。 */
    data class Publish(val tasks: Map<String, DownloadTask>) : ForegroundAction

    /** 这一轮被节流挡下，[delayMs] 之后再补发一次进度。 */
    data class ScheduleFlush(val delayMs: Long) : ForegroundAction
}

/**
 * 下载前台服务的策略：只做决策、不碰 Android API。
 *
 * 设计要点（与 ENGINEERING.md 附录 A.6 / A.7 一致）：
 * - 生命周期唯一驱动量是活动任务数（QUEUED + RUNNING），PAUSED/DONE/FAILED 一律不占前台；
 * - 只在活动任务数变化时尝试拉起，集合不变的重试就是保活，[startRetryIntervalMs] 只压同一批任务内的抖动；
 * - 进度按 [progressIntervalMs] 节流，任务进终态时绕过节流立即刷新，被挡下的那一次排 [ForegroundAction.ScheduleFlush] 补发；
 * - 服务是否真的在前台由调用方通过 [onServiceForeground] / [onServiceStopped] 回写，策略自己不去拉起。
 */
class ForegroundPolicy(
    private val startRetryIntervalMs: Long = DEFAULT_START_RETRY_INTERVAL_MS,
    private val progressIntervalMs: Long = DEFAULT_PROGRESS_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val throttle = DownloadThrottle(progressIntervalMs)

    /**
     * 服务是否已进入前台（由服务实例回写）。
     *
     * 回写发生在主线程、读取发生在订阅队列的协程，因此这两个字段用 @Volatile 保证可见性；
     * [onTasks] 本身只允许由一个协程串行调用，不承担并发写入。
     */
    @Volatile
    private var running = false

    /** 上次已知的活动任务数，用于去重：静止状态不重复 start/stop。 */
    @Volatile
    private var lastActiveCount = -1

    /** 上次拉起尝试的时间：只用于压同一次任务数变化内的抖动。 */
    private var lastStartAttemptAt = 0L

    /**
     * 上次拉起失败的时间：失败后即使活动任务数不变，也要在退避窗口外按时间再试一次。
     *
     * 只记录失败时间、不依赖「下一次任务数变化」，否则队列静止时服务会一直起不来。
     */
    @Volatile
    private var lastStartFailedAt = 0L

    /** 上一次活动任务快照：用来识别终态跃迁。 */
    private var previous: Map<String, DownloadTask> = emptyMap()

    val isRunning: Boolean get() = running

    /** 服务已进前台（onStartCommand 里 startForeground 成功）。 */
    fun onServiceForeground() {
        running = true
        lastStartFailedAt = 0L
    }

    /** 服务已退出前台（onDestroy、启动失败或超时退场）。 */
    fun onServiceStopped() {
        running = false
    }

    /**
     * 拉起被系统拒绝：记下失败时间并回滚活动数快照。
     *
     * 只记失败时间不够——[onTasks] 在活动数不变时不会再发 Start；只回滚计数也不够——
     * 若队列静止就没有下一次更新。两者一起做：下一次队列更新到来时，无论计数是否变化，
     * 只要退避窗口（[startRetryIntervalMs]）已过就重新尝试。
     */
    fun onStartFailed() {
        lastActiveCount = -1
        lastStartFailedAt = clock()
    }

    /**
     * 队列状态变化时调用，返回这一轮要执行的动作。
     *
     * 顺序即执行顺序：先拉起/停止，再决定这一轮的通知怎么发。
     */
    fun onTasks(tasks: Map<String, DownloadTask>): List<ForegroundAction> {
        val actions = ArrayList<ForegroundAction>(2)
        val active = tasks.activeDownloadCount()
        val countChanged = active != lastActiveCount
        lastActiveCount = active

        if (active > 0) {
            if (!running && shouldStart(countChanged) && canAttemptStart()) actions += ForegroundAction.Start
            if (running) {
                val immediate = countChanged || tasks.hasTerminalTransitionFrom(previous)
                if (throttle.shouldEmit(clock(), immediate)) {
                    actions += ForegroundAction.Publish(tasks)
                } else {
                    actions += ForegroundAction.ScheduleFlush(progressIntervalMs)
                }
            }
        } else if (running) {
            running = false
            actions += ForegroundAction.Stop
        }
        previous = tasks
        return actions
    }

    /** 补发窗口到期：服务还在前台才发，否则这条进度已经没有意义。 */
    fun onFlushDue(tasks: Map<String, DownloadTask>): ForegroundAction? =
        if (running) ForegroundAction.Publish(tasks) else null

    /** 活动数变化才拉起；上一次拉起失败的话，退避窗口过后按时间补一次。 */
    private fun shouldStart(countChanged: Boolean): Boolean {
        if (countChanged) return true
        val failedAt = lastStartFailedAt
        return failedAt != 0L && clock() - failedAt >= startRetryIntervalMs
    }

    private fun canAttemptStart(): Boolean {
        val now = clock()
        if (lastStartAttemptAt != 0L && now - lastStartAttemptAt < startRetryIntervalMs) return false
        lastStartAttemptAt = now
        return true
    }

    companion object {
        /** 同一批任务内两次拉起尝试的最小间隔：只压抖动，不构成保活。 */
        const val DEFAULT_START_RETRY_INTERVAL_MS = 5_000L

        /** 通知进度节流窗口：展示侧不再自带同名常量，全应用只有这一个值。 */
        const val DEFAULT_PROGRESS_INTERVAL_MS = 300L
    }
}
