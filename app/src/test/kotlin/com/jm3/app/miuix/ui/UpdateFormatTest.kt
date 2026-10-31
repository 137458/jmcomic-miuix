package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 更新页的字节换算契约。
 *
 * 下载量与速度共用同一套刻度，所以两者必须落在同一单位档位：未知长度（0 或负值）
 * 显示 0 B 而不是怪异的负数，KB 档不出现小数，MB 档保留一位小数。
 */
class UpdateFormatTest {

    @Test
    fun `unknown length collapses to zero bytes`() {
        assertEquals("0 B", formatUpdateSize(0))
        assertEquals("0 B", formatUpdateSize(-4096))
    }

    @Test
    fun `byte band keeps raw bytes without decimals`() {
        assertEquals("1 B", formatUpdateSize(1))
        assertEquals("1023 B", formatUpdateSize(1023))
    }

    @Test
    fun `kilobyte band rounds to whole kilobytes`() {
        assertEquals("1 KB", formatUpdateSize(1024))
        assertEquals("2 KB", formatUpdateSize(1536))
        assertEquals("10 KB", formatUpdateSize(10L * 1024))
    }

    @Test
    fun `megabyte band keeps one decimal`() {
        assertEquals("1.0 MB", formatUpdateSize(1024L * 1024))
        assertEquals("1.5 MB", formatUpdateSize(1024L * 1024 * 3 / 2))
        assertEquals("50.0 MB", formatUpdateSize(50L * 1024 * 1024))
    }

    @Test
    fun `speed appends per second suffix on the same scale`() {
        assertEquals("0 B/s", formatUpdateSpeed(0))
        assertEquals("2 KB/s", formatUpdateSpeed(2048))
        assertEquals("2.5 MB/s", formatUpdateSpeed(1024L * 1024 * 5 / 2))
    }
}
