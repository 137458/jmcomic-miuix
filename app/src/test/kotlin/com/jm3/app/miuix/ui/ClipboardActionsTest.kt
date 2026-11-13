package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ClipboardActionsTest {

    @Test
    fun 写入成功才提示复制成功() {
        assertEquals("复制成功！", clipboardCopyMessage(copied = true))
    }

    @Test
    fun 写入失败必须提示复制失败而不是成功() {
        // 系统剪贴板不可用时 setPrimaryClip 根本没执行，此处必须报失败
        assertEquals("复制失败，请重试", clipboardCopyMessage(copied = false))
        assertNotEquals(clipboardCopyMessage(copied = true), clipboardCopyMessage(copied = false))
    }

    @Test
    fun 调用方可保留自己的成功文案但失败文案仍统一() {
        assertEquals("已复制版本号 v1.2.3", clipboardCopyMessage(copied = true, successMessage = "已复制版本号 v1.2.3"))
        assertEquals("复制失败，请重试", clipboardCopyMessage(copied = false, successMessage = "已复制版本号 v1.2.3"))
    }
}
