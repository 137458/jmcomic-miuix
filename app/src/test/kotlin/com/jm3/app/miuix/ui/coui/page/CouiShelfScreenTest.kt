// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.download.presentRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 书架卡片副标题的车号口径与进度拼接。
 *
 * 车号要跟详情页同一套判据（空串与 "-1" 都算没有车号），否则会拼出孤立的 "# ·" 或指向无效封面；
 * 进度取行口径 [presentRow] 的 donePages，续传后超出总量的游标在副标题里同样被夹回总量。
 */
class CouiShelfScreenTest {

    private fun record(
        albumId: String,
        total: Int = 12,
        donePages: Int = 3,
        status: DownloadStatus = DownloadStatus.RUNNING,
    ): DownloadRecord = DownloadRecord(
        photoId = "photo-1",
        albumId = albumId,
        title = "作品标题",
        total = total,
        donePages = donePages,
        status = status,
        updatedAt = 1_700_000_000_000L,
    )

    @Test
    fun 有车号时副标题以井号拼车号与进度() {
        val record = record(albumId = "123456")
        assertEquals("#123456 · 下载中 3/12", couiShelfSummary(record, presentRow(record), "下载中"))
    }

    @Test
    fun 车号两侧空白在拼接前被去掉() {
        val record = record(albumId = "  123456  ", total = 4, donePages = 0, status = DownloadStatus.PAUSED)
        assertEquals("123456", couiShelfAlbumId(record))
        assertEquals("#123456 · 已暂停 0/4", couiShelfSummary(record, presentRow(record), "已暂停"))
    }

    @Test
    fun 空车号只显示状态与进度() {
        val blank = record(albumId = "")
        val spaces = record(albumId = "   ")
        assertNull(couiShelfAlbumId(blank))
        assertNull(couiShelfAlbumId(spaces))
        assertEquals("下载中 3/12", couiShelfSummary(blank, presentRow(blank), "下载中"))
        assertEquals("下载中 3/12", couiShelfSummary(spaces, presentRow(spaces), "下载中"))
    }

    @Test
    fun 车号为减一同样视为无车号() {
        val record = record(albumId = "-1")
        assertNull(couiShelfAlbumId(record))
        assertEquals("下载中 3/12", couiShelfSummary(record, presentRow(record), "下载中"))
    }

    @Test
    fun 完成态副标题仍报全量页数() {
        val record = record(albumId = "777", total = 12, donePages = 12, status = DownloadStatus.DONE)
        assertEquals("#777 · 已完成 12/12", couiShelfSummary(record, presentRow(record), "已完成"))
    }

    @Test
    fun 续传游标超出总量时按总量夹取() {
        // 续传后 donePages 可能大于重新解析出的 total，行口径先夹取，副标题不能出现 15/12
        val record = record(albumId = "888", total = 12, donePages = 15, status = DownloadStatus.PAUSED)
        assertEquals("#888 · 已暂停 12/12", couiShelfSummary(record, presentRow(record), "已暂停"))
    }
}
