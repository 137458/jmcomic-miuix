package com.jm3.app.miuix.data.download

import com.jm3.app.miuix.R
import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 选型 -> 字符串资源的映射：core 给选型，:app 只负责一一对应的资源。
 * 这里逐条钉死「哪个选型落哪条资源」：只断言「互不相同」时，两条映射互换（暂停说成下载中、
 * 继续与重试换文案）仍然全绿，而那正是用户直接看得见的错。
 */
class DownloadRowTextTest {

    @Test
    fun `五种标签各落各的资源`() {
        val expected = mapOf(
            DownloadLabel.Queued to R.string.download_status_queued,
            DownloadLabel.Running to R.string.download_status_running,
            DownloadLabel.Paused to R.string.download_status_paused,
            DownloadLabel.Done to R.string.download_status_done,
            DownloadLabel.Failed to R.string.download_status_failed,
        )
        expected.forEach { (label, res) ->
            assertEquals(res, label.textRes(), "标签 $label 落错了资源")
        }
    }

    @Test
    fun `四种动作各落各的图标描述`() {
        val expected = mapOf(
            DownloadAction.Open to R.string.download_action_open,
            DownloadAction.Pause to R.string.download_action_pause,
            DownloadAction.Resume to R.string.download_action_resume,
            DownloadAction.Retry to R.string.download_action_retry,
        )
        assertEquals(expected.size, DownloadAction.entries.size, "新增动作必须同步补资源断言")
        expected.forEach { (action, res) ->
            assertEquals(res, action.contentDescriptionRes(), "动作 $action 落错了资源")
        }
    }
}
