package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatePromptTest {

    @Test
    fun 未忽略的新版本需要提示() {
        assertTrue(
            UpdatePrompt.shouldNotify(
                latestVersion = "1.2.0",
                ignoredVersion = null,
                promptedVersion = null,
            ),
        )
    }

    @Test
    fun 已被忽略的同版本不再提示() {
        assertFalse(
            UpdatePrompt.shouldNotify(
                latestVersion = "1.2.0",
                ignoredVersion = "1.2.0",
                promptedVersion = null,
            ),
        )
    }

    @Test
    fun 忽略的旧版本不影响更高版本提示() {
        assertTrue(
            UpdatePrompt.shouldNotify(
                latestVersion = "1.3.0",
                ignoredVersion = "1.2.0",
                promptedVersion = null,
            ),
        )
    }

    @Test
    fun 启动期已提示过的版本不重复弹窗() {
        assertFalse(
            UpdatePrompt.shouldNotify(
                latestVersion = "1.2.0",
                ignoredVersion = null,
                promptedVersion = "1.2.0",
            ),
        )
    }

    @Test
    fun 空白版本串按未设置处理() {
        assertFalse(UpdatePrompt.isIgnored(ignoredVersion = "  ", latestVersion = ""))
        assertTrue(UpdatePrompt.isIgnored(ignoredVersion = " 1.2.0 ", latestVersion = "1.2.0"))
    }

    @Test
    fun 忽略开关在已忽略时清除记录() {
        assertNull(UpdatePrompt.toggleIgnored(currentIgnored = "1.2.0", latestVersion = "1.2.0"))
    }

    @Test
    fun 忽略开关在未忽略时记录当前版本() {
        assertEquals("1.2.0", UpdatePrompt.toggleIgnored(currentIgnored = null, latestVersion = "1.2.0"))
        assertEquals("1.2.0", UpdatePrompt.toggleIgnored(currentIgnored = "1.1.0", latestVersion = "1.2.0"))
    }

    @Test
    fun 无可忽略版本时开关保持关闭() {
        assertNull(UpdatePrompt.toggleIgnored(currentIgnored = null, latestVersion = "  "))
    }
}
