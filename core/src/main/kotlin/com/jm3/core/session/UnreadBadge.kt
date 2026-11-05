package com.jm3.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 信箱未读角标：一个计数、一条刷新规则。
 *
 * 「未登录归零、失败保留上次」原先写在应用层依赖图里，既测不到（要网关、要账号、要进程作用域），
 * 也让角标闪烁的判定散落在界面之外。计数与规则收在这里之后，调用方只剩 refresh(loggedIn)。
 */
class UnreadBadge(private val load: suspend () -> Int) {

    private val state = MutableStateFlow(0)

    /** 当前未读数；界面直接 collect。 */
    val count: StateFlow<Int> = state.asStateFlow()

    /** 重新统计：未登录归零且不读网络；拉取失败保留上次值。 */
    suspend fun refresh(loggedIn: Boolean) {
        if (!loggedIn) {
            state.value = 0
            return
        }
        runCatching { load() }.onSuccess { state.value = it }
    }

    /** 以刚拉到的列表为准覆盖计数：同一次请求里读到的已读状态比上一次的远端旧值可靠。 */
    fun sync(count: Int) {
        state.value = count
    }
}
