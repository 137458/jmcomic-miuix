package com.jm3.app.miuix.ui

import com.jm3.app.miuix.update.UpdateDownloadState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 更新弹窗主按钮的文案契约。
 *
 * 弹窗的显隐由页面管，这里只钉住一件用户能看见的事：主按钮这一格里到底写什么。两种维度参与
 * 决策——有没有安装包、下载到了哪一步；下载中要把百分比写进按钮，其它状态都要回到"立即下载"，
 * 否则下载失败后按钮会停在"下载中"，用户点不动也不知道该重试。
 */
class UpdateDialogTest {

    private fun downloading(downloaded: Long, total: Long) =
        UpdateDownloadState.Downloading(downloaded = downloaded, total = total, speedBytesPerSec = 1_000_000L)

    @Test
    fun `idle state with apk offers download`() {
        assertEquals(
            "立即下载",
            updateDialogPrimaryText(hasApk = true, downloadState = UpdateDownloadState.Idle),
        )
    }

    @Test
    fun `idle state without apk falls back to releases page`() {
        assertEquals(
            "前往 Releases 页面",
            updateDialogPrimaryText(hasApk = false, downloadState = UpdateDownloadState.Idle),
        )
    }

    @Test
    fun `download progress is written into the primary button`() {
        assertEquals(
            "下载中 87%",
            updateDialogPrimaryText(hasApk = true, downloadState = downloading(87, 100)),
        )
        // 除不尽时截断而不是进位：未下满不能显示成 100%
        assertEquals(
            "下载中 33%",
            updateDialogPrimaryText(hasApk = true, downloadState = downloading(1, 3)),
        )
    }

    @Test
    fun `unknown total length reports zero instead of jumping`() {
        assertEquals("下载中 0%", updateDialogProgressText(downloading(4_096, 0)))
    }

    @Test
    fun `progress is clamped to a hundred percent`() {
        assertEquals("下载中 100%", updateDialogProgressText(downloading(3, 2)))
    }

    @Test
    fun `settled download returns to the download label`() {
        val file = File("/tmp/jm3-9.9.9-test.apk")
        assertEquals(
            "立即下载",
            updateDialogPrimaryText(hasApk = true, downloadState = UpdateDownloadState.Done(file)),
        )
        assertEquals(
            "立即下载",
            updateDialogPrimaryText(hasApk = true, downloadState = UpdateDownloadState.Failed("网络中断")),
        )
    }

    @Test
    fun `missing apk keeps the releases label even mid download`() {
        assertEquals(
            "前往 Releases 页面",
            updateDialogPrimaryText(hasApk = false, downloadState = downloading(87, 100)),
        )
    }
}
