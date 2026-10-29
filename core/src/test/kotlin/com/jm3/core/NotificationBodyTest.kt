package com.jm3.core

import com.jm3.core.model.NotificationBody
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationUpdate
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationBodyTest {

    private fun item(
        title: String = "",
        content: String = "",
        type: String? = null,
    ) = NotificationItem(id = "1", title = title, content = content, time = null, read = false, type = type)

    @Test
    fun siteNoticeHtmlBecomesParagraphText() {
        val body = notificationBody("<p>第一行</p><p>第二行&nbsp;&amp;</p>")
        assertEquals(NotificationBody.Text("第一行\n第二行 &"), body)
    }

    @Test
    fun followNoticeJsonArrayBecomesUpdates() {
        val content = """[{"comicId":123456,"comicTitle":"作品甲","updateDate":"2024-05-01"},""" +
            """{"comicId":"789","comicTitle":"作品乙","updateDate":"2024-05-02"}]"""
        assertEquals(
            NotificationBody.Updates(
                listOf(
                    NotificationUpdate(comicId = "123456", comicTitle = "作品甲", updateDate = "2024-05-01"),
                    NotificationUpdate(comicId = "789", comicTitle = "作品乙", updateDate = "2024-05-02"),
                ),
            ),
            notificationBody(content),
        )
    }

    @Test
    fun updatesWithoutTitleAreSkippedAndMissingFieldsDegrade() {
        val content = """[{"comicId":1,"comicTitle":""},{"comicTitle":"作品丙"},""" +
            """{"comicId":3,"comicTitle":"作品丁"}]"""
        assertEquals(
            NotificationBody.Updates(
                listOf(
                    NotificationUpdate(comicId = null, comicTitle = "作品丙", updateDate = null),
                    NotificationUpdate(comicId = "3", comicTitle = "作品丁", updateDate = null),
                ),
            ),
            notificationBody(content),
        )
    }

    @Test
    fun emptyArrayBecomesEmptyUpdates() {
        assertEquals(NotificationBody.Updates(emptyList()), notificationBody(" [] "))
    }

    @Test
    fun brokenJsonFallsBackToPlainText() {
        val raw = "[连载更新]你追踪的漫画有 2 部作品更新"
        assertEquals(NotificationBody.Text(raw), notificationBody(raw))
    }

    @Test
    fun blankContentBecomesEmptyText() {
        assertEquals(NotificationBody.Text(""), notificationBody(null))
        assertEquals(NotificationBody.Text(""), notificationBody("   "))
    }

    @Test
    fun followNoticeTitleUsesUpdateCount() {
        val content = """[{"comicId":1,"comicTitle":"作品甲"},{"comicId":2,"comicTitle":"作品乙"}]"""
        assertEquals(
            "[连载更新]你追踪的漫画有 2 部作品更新",
            notificationDisplayTitle(item(title = "连载更新", content = content, type = NOTIFICATION_TYPE_FOLLOW)),
        )
    }

    @Test
    fun followNoticeFallsBackToApiTitleWhenBodyIsNotArray() {
        assertEquals(
            "服务器维护通知",
            notificationDisplayTitle(
                item(title = "服务器维护通知", content = "<p>今晚维护</p>", type = NOTIFICATION_TYPE_FOLLOW),
            ),
        )
    }

    @Test
    fun siteNoticeTitleUsesApiTitle() {
        assertEquals(
            "服务器维护通知",
            notificationDisplayTitle(item(title = " 服务器维护通知 ", content = "<p>今晚维护</p>", type = "site_notice")),
        )
        assertEquals("站内通知", notificationDisplayTitle(item(title = "", content = "", type = "site_notice")))
    }
}
