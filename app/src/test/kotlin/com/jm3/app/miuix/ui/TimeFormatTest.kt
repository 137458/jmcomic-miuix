package com.jm3.app.miuix.ui

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    @Test
    fun 格式化器不绑定时区同一实例可切换时区() {
        val epochSeconds = 1_700_000_000L
        assertEquals("2023-11-14 22:13", formatEpochSeconds(epochSeconds, formatter, ZoneId.of("UTC")))
        assertEquals("2023-11-15 06:13", formatEpochSeconds(epochSeconds, formatter, ZoneId.of("Asia/Shanghai")))
    }

    @Test
    fun 毫秒格式化同样按调用时区换算() {
        val epochMillis = 1_700_000_000_000L
        assertEquals("2023-11-14 22:13", formatEpochMillis(epochMillis, formatter, ZoneId.of("UTC")))
        assertEquals("2023-11-15 06:13", formatEpochMillis(epochMillis, formatter, ZoneId.of("Asia/Shanghai")))
    }

    @Test
    fun 非正秒级时间戳返回空串() {
        assertEquals("", formatEpochSeconds(0L, formatter, ZoneId.of("UTC")))
        assertEquals("", formatEpochSeconds(-1L, formatter, ZoneId.of("UTC")))
    }
}
