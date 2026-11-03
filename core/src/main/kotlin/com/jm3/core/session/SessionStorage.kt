package com.jm3.core.session

import com.jm3.core.model.Jm3Session

/** 会话持久化契约：只存一份最新快照，冷启动优先用它恢复。 */
interface SessionStorage {
    suspend fun load(): Jm3Session?
    suspend fun save(session: Jm3Session)
    suspend fun clear()
}
