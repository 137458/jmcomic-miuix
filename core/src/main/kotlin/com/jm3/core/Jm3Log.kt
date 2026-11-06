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

    /** 记一条事实日志。:app 也要用它（启动装配的失败只在这里留得下痕迹），所以是公开出口。 */
    fun warn(tag: String, message: String, error: Throwable? = null) {
        sink?.invoke(tag, message, error)
    }
}

/**
 * 反射写库内状态这类「失败也能继续跑」的动作：出错记一条事实日志再吞掉。
 *
 * 这类动作失败不抛给用户，界面上只表现为「自动重登没生效」「会话恢复不完整」，
 * 不留日志就完全无从查起。只报动作名与异常类型，不打印成员值。
 */
internal fun quietly(tag: String, what: String, block: () -> Unit) {
    runCatching(block).onFailure { failure ->
        Jm3Log.warn(tag, what + " 失败（" + failure.javaClass.name + "），相关能力降级")
    }
}
