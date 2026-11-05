package com.jm3.core.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动闸门与失效通知：账号层提供给 [AuthCall] 的两件事。
 *
 * 只抽这两个方法而不是整张 [AccountManager]：调用方需要的承诺只有「恢复结束前不放行」与
 * 「失效时通知账号层」，其余（重登、凭据、线路切换）都不属于调用方要知道的事。
 */
interface StartupGate {
    /** 挂起到启动恢复尝试结束；恢复失败也放行，让调用方拿到真实的网络错误。 */
    suspend fun awaitReady()

    /** 会话已被判定失效：交给账号层决定是补注入、静默重登还是真的清会话。 */
    suspend fun onSessionExpired()
}

/**
 * 一次需要登录态的调用的唯一入口：先过启动闸门、在 IO 上执行、把结局分成三种。
 *
 * 为什么要有它：这三步原先由 13 个屏幕各自手写（awaitRestored → withContext(IO) →
 * sessionOutcome{} → 何时清会话），漏一步就是「冷启动带空 Cookie 发请求、401 被翻译成
 * 登录过期」或「主线程联网」。放在这里之后，屏幕只需一次 when 三支。
 *
 * 失效处置的异常被吞掉：账号层清会话失败（磁盘只读、存储抛错）不该把用户卡在空白页，
 * 界面仍应显示「登录已失效」并让用户重新登录。
 */
class AuthCall(
    private val gate: StartupGate,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun <T> call(block: suspend () -> T): SessionOutcome<T> {
        try {
            gate.awaitReady()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            return classify(e)
        }

        val outcome: SessionOutcome<T> =
            try {
                SessionOutcome.Ok(withContext(io) { block() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                classify(e)
            }

        if (outcome === SessionOutcome.Expired) {
            try {
                withContext(io) { gate.onSessionExpired() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 处置失败不改变结局：界面照样要显示失效并引导重新登录。
            }
        }
        return outcome
    }
}
