package com.jm3.core

/**
 * 事实日志出口。
 *
 * :core 是纯 JVM 模块，拿不到 android.util.Log，所以只留这一处出口，由 :app 在启动时接到平台日志
 * （Jm3Graph.init）。没接的地方（单元测试、纯 JVM 调用）静默丢弃，不改变任何业务流程。
 *
 * 只写事实：成员名、字段名、异常类型；绝不打印成员值——密码与 Cookie 都躺在这些成员里。
 */
object Jm3Log {

    /** 平台日志的落点；tag 是调用方给的稳定短名。 */
    @Volatile
    var sink: ((tag: String, message: String, error: Throwable?) -> Unit)? = null

    internal fun warn(tag: String, message: String, error: Throwable? = null) {
        sink?.invoke(tag, message, error)
    }
}
