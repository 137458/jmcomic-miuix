package com.jm3.core

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] 的协程安全版本：取消原样抛出，只有真实失败收进 [Result]。
 *
 * runCatching 会把 CancellationException 一起吞掉，屏幕已经销毁后剩下的逻辑仍会继续跑完
 * （见 ENGINEERING.md 三.1；范本是 DownloadQueue.workerLoop 的 catch 顺序）。
 */
suspend fun <T> runCatchingSuspending(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.failure(e)
    }
