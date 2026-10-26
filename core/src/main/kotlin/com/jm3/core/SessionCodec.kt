package com.jm3.core

import com.jm3.core.model.Jm3Session
import kotlinx.serialization.json.Json

/** 会话与本地 JSON 互转；decode 对损坏输入返回 null。 */
object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(session: Jm3Session): String = json.encodeToString(Jm3Session.serializer(), session)

    fun decode(raw: String): Jm3Session? =
        runCatching { json.decodeFromString(Jm3Session.serializer(), raw) }.getOrNull()
}
