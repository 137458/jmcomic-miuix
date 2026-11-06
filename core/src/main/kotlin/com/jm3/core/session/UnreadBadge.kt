package com.jm3.core.session

import com.jm3.core.runCatchingSuspending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 信箱未读角标：一个计数、一条刷新规则。
 *
 * 「未登录归零、失败保留上次」原先写在应用层依赖图里，既测不到（要网关、要账号、要进程作用域），
 * 也让角标闪烁的判定散落在界面之外。计数与规则收在这里之后，调用方只剩 refresh(loggedIn)。
 *
 * [load] 返回 [SessionOutcome]：未读数请求同样要过启动闸门、要能判定会话失效，所以由调用方
 * （装配处）注入走 AuthCall 的实现；本类只决定「哪种结局覆盖计数」。
 */
class UnreadBadge(private val load: suspend () -> SessionOutcome<Int>) {

    private val state = MutableStateFlow(0)

    /** 当前未读数；界面直接 collect。 */
    val count: StateFlow<Int> = state.asStateFlow()

    /** 重新统计：未登录归零且不读网络；拉取失败保留上次值。 */
    suspend fun refresh(loggedIn: Boolean) {
        if (!loggedIn) {
            state.value = 0
            return
        }
        runCatchingSuspending { load() }.onSuccess { outcome ->
            // 只有拿到值才覆盖：失效与失败都保留上次计数，否则底栏角标会闪一下又归零
            if (outcome is SessionOutcome.Ok) state.value = outcome.value
        }
    }

    /** 以刚拉到的列表为准覆盖计数：同一次请求里读到的已读状态比上一次的远端旧值可靠。 */
    fun sync(count: Int) {
        state.value = count
    }
}
