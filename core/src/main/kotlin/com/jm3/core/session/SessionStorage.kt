package com.jm3.core.session

import com.jm3.core.model.Jm3Session

/** 会话持久化契约：只存一份最新快照，冷启动优先用它恢复。 */
interface SessionStorage {
    suspend fun load(): Jm3Session?
    suspend fun save(session: Jm3Session)
    suspend fun clear()

    /**
     * 静默重登连续失败次数。
     *
     * 计数必须落盘：一次失败后当前会话就被清掉，要等下次冷启动才会再试一次，
     * 只记在内存里永远攒不到阈值。
     */
    suspend fun renewFailureCount(): Int

    /** 写入静默重登连续失败次数；重登成功或凭据已被清掉时写 0。 */
    suspend fun setRenewFailureCount(count: Int)
}
