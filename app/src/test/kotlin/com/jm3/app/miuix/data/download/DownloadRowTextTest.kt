package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 选型 -> 字符串资源的映射：core 给选型，:app 只负责一一对应的资源。
 * 这里锁住「不同选型不能落到同一条资源」：标签重复会让书架把暂停显示成下载中，
 * 动作重复会让「重试」和「继续」变成同一个按钮文案。
 */
class DownloadRowTextTest {

    @Test
    fun `五种标签各自对应不同资源`() {
        val res = listOf(
            DownloadLabel.Queued,
            DownloadLabel.Running,
            DownloadLabel.Paused,
            DownloadLabel.Done,
            DownloadLabel.Failed,
        ).map { it.textRes() }
        assertEquals(5, res.toSet().size)
        assertNotEquals(DownloadLabel.Running.textRes(), DownloadLabel.Paused.textRes())
    }

    @Test
    fun `四种动作各自对应不同资源`() {
        val res = DownloadAction.entries.map { it.contentDescriptionRes() }
        assertEquals(4, res.toSet().size)
        assertNotEquals(DownloadAction.Resume.contentDescriptionRes(), DownloadAction.Retry.contentDescriptionRes())
    }
}
