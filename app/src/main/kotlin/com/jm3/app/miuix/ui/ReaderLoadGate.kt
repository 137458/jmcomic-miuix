package com.jm3.app.miuix.ui

/**
 * 阅读器首载/重试的代际闸门。
 *
 * 连点重试或换章时可能同时有多条 `gateway.photo` 在途；每次加载先 [start] 取得新代际，
 * 结局写回前用 [isLatest] 校验自己仍是最新一次，旧响应直接丢弃，不覆盖新状态。
 * 只做整数比较，测试不依赖协程时序。
 */
internal class ReaderLoadGate {
    private var latest = 0

    fun start(): Int {
        latest += 1
        return latest
    }

    fun isLatest(token: Int): Boolean = token == latest
}
