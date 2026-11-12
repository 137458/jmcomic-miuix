package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadTask
import com.jm3.core.download.ForegroundAction
import com.jm3.core.download.ForegroundPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 队列快照到前台动作的执行器，外加「拉起被拒后按时间补评估」。
 *
 * 不碰 Android API：[perform] 由服务注入（拉起/停止/发进度/排补发），退避逻辑因此能在虚拟时间里验证。
 * 三条不变量：
 * - 补评估单飞：同一时刻最多排一次，已排期不叠加；
 * - 本轮会产生 [ForegroundAction.Start] 时撤销未到点的补评估，避免同一批任务被拉两次；
 * - 补评估到点后重读 [latestTasks]，不拿失败那一刻的旧快照做决策。
 *
 * 排期字段由 [lock] 串行化：观察者协程与补评估协程都挂在进程级 IO 作用域上，可能并发进入。
 */
internal class ForegroundRunner(
    private val policy: ForegroundPolicy,
    private val scope: CoroutineScope,
    private val retryIntervalMs: Long,
    private val latestTasks: () -> Map<String, DownloadTask>,
    private val perform: (ForegroundAction) -> Boolean,
    private val lock: Any = Any(),
) {

    /** 未到点的补评估；null 表示没有排期。 */
    private var retryJob: Job? = null

    /** 队列新快照：重新决策，逐个执行这一轮的动作。 */
    fun onTasks(tasks: Map<String, DownloadTask>) {
        synchronized(lock) { evaluateLocked(tasks) }
    }

    private fun evaluateLocked(tasks: Map<String, DownloadTask>) {
        val actions = policy.onTasks(tasks)
        if (ForegroundAction.Start in actions) cancelRetryLocked()
        actions.forEach(::executeLocked)
    }

    private fun executeLocked(action: ForegroundAction) {
        // 只有拉起会失败（系统拒绝后台启动前台服务）；其余动作没有可重试的退避语义。
        if (action == ForegroundAction.Start && !perform(action)) scheduleRetryLocked()
    }

    private fun scheduleRetryLocked() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(retryIntervalMs)
            synchronized(lock) {
                // 到点即消费本次排期：这一轮仍失败会重新排一次，不会叠成自旋。
                retryJob = null
                evaluateLocked(latestTasks())
            }
        }
    }

    private fun cancelRetryLocked() {
        retryJob?.cancel()
        retryJob = null
    }
}
