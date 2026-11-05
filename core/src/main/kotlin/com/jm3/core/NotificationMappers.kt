package com.jm3.core

import com.jm3.core.model.NotificationBody
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import com.jm3.core.model.NotificationUpdate
import com.jm3.core.model.TrackingItem
import com.jm3.core.model.TrackingPage
import io.github.jukomu.jmcomic.api.model.JmNotificationPage
import io.github.jukomu.jmcomic.api.model.JmTrackingPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 通知列表；标题/正文缺失时降级为空串，已读态直接透传。 */
fun JmNotificationPage.toNotificationPage(): NotificationPage = NotificationPage(
    total = total(),
    items = list().orEmpty().map { item ->
        NotificationItem(
            id = item.id().orEmpty(),
            title = item.title().orEmpty(),
            content = item.content().orEmpty(),
            time = item.time(),
            read = item.read(),
            type = item.type(),
        )
    },
)

/** 追更通知的类型标识，与接口 type 字段一致。 */
const val NOTIFICATION_TYPE_FOLLOW: String = "comic_follow"

/**
 * 解析通知正文。接口 content 有两种形态：站内通知是 HTML，追更通知是作品更新数组；
 * 库的 ApiParser 把数组整体 toString，因此拿到的仍是 "[{...}]" 文本。数组解析失败时
 * 按纯文本降级，避免把 JSON 原文直接显示给用户。
 */
fun notificationBody(content: String?): NotificationBody {
    val raw = content?.trim().orEmpty()
    if (raw.isEmpty()) return NotificationBody.Text("")
    if (raw.startsWith("[")) {
        val array = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
        if (array != null) {
            return NotificationBody.Updates(array.mapNotNull { it.toNotificationUpdate() })
        }
    }
    return NotificationBody.Text(htmlToText(raw))
}

private fun JsonElement.toNotificationUpdate(): NotificationUpdate? {
    val fields = this as? JsonObject ?: return null
    val title = fields.fieldText("comicTitle") ?: return null
    return NotificationUpdate(
        comicId = fields.fieldText("comicId"),
        comicTitle = title,
        updateDate = fields.fieldText("updateDate"),
    )
}

private fun JsonObject.fieldText(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

/**
 * 信箱条目展示标题。追更通知按更新条数生成（对照原版 NotificationList：
 * 「[连载更新]你追踪的漫画有 N 部作品更新」），站内通知直接用接口标题。
 */
fun notificationDisplayTitle(item: NotificationItem): String {
    val apiTitle = item.title.trim()
    if (item.type != NOTIFICATION_TYPE_FOLLOW) return apiTitle.ifBlank { "站内通知" }
    val updates = (notificationBody(item.content) as? NotificationBody.Updates)?.items.orEmpty()
    if (updates.isEmpty()) return apiTitle.ifBlank { "连载更新" }
    return "[连载更新]你追踪的漫画有 ${updates.size} 部作品更新"
}

/** 连载追踪列表；totalCnt 为字符串类型，无法解析时按当前页条数计。 */
fun JmTrackingPage.toTrackingPage(): TrackingPage {
    val items = item().orEmpty().map { entry ->
        TrackingItem(
            id = entry.id().orEmpty(),
            name = entry.name().orEmpty(),
            coverUrl = entry.image()?.takeIf { it.isNotBlank() },
            updateAt = entry.updateAt()?.takeIf { it.isNotBlank() },
        )
    }
    return TrackingPage(
        total = totalCnt()?.toIntOrNull() ?: items.size,
        items = items,
    )
}
