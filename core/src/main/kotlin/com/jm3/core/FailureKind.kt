package com.jm3.core

import io.github.jukomu.jmcomic.api.exception.JmClientInitializationException
import io.github.jukomu.jmcomic.api.exception.NetworkException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 失败结局的归类档位：把「异常类型到用户可读文案」的判定关在 :core 内。
 *
 * 为什么放在这里：协议库的异常类型只允许出现在 :core（ENGINEERING.md §二.4），
 * 屏幕侧要的是「哪一档失败」，不是库的异常类；归类留下，`:app` 才能不带协议依赖。
 */
enum class FailureKind {
    /** 会话失效：按文案判，见 [isSessionExpired]。 */
    LoginExpired,

    /** 客户端尚未就绪（域名全挂、初始化失败）。 */
    ClientNotReady,

    /** 读超时一类的可重试网络问题。 */
    Timeout,

    /** 连不上、解析不了域名等网络失败。 */
    Network,

    /** 其余异常，由调用方给入口自己的兜底文案。 */
    Other,
}

/**
 * 按「文案 → 异常链类型」的顺序归类一次失败。
 *
 * 顺序不能换：初始化异常内部常包着超时或网络异常，先判外层类型才能给出「客户端未就绪」，
 * 否则会被误报成普通网络失败。
 */
fun classifyFailure(message: String?, cause: Throwable?): FailureKind = when {
    isSessionExpired(message) -> FailureKind.LoginExpired
    cause.hasType(JmClientInitializationException::class.java) -> FailureKind.ClientNotReady
    cause.hasType(SocketTimeoutException::class.java) -> FailureKind.Timeout
    cause.hasType(InterruptedIOException::class.java) -> FailureKind.Timeout
    cause.hasNetworkType() -> FailureKind.Network
    else -> FailureKind.Other
}

/** 沿异常链找类型：库常把底层 IOException 包在自己的异常里，只看顶层的类型会漏判。 */
private fun Throwable?.hasType(type: Class<*>): Boolean {
    var current = this
    val visited = HashSet<Throwable>()
    while (current != null && visited.add(current)) {
        if (type.isInstance(current)) return true
        current = current.cause
    }
    return false
}

private fun Throwable?.hasNetworkType(): Boolean =
    hasType(NetworkException::class.java) ||
        hasType(UnknownHostException::class.java) ||
        hasType(ConnectException::class.java) ||
        hasType(NoRouteToHostException::class.java)
