package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 按当前界面语言构建的只读日期格式化器。
 *
 * 格式化器只记住 locale，不绑定时区；时区在每次格式化时从 [ZoneId.systemDefault] 取，
 * 系统时区变化后无需重建格式化器就能得到新时区的时间。
 */
@Composable
internal fun rememberDateFormatter(pattern: String = "yyyy-MM-dd HH:mm"): DateTimeFormatter {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) Locale.ROOT else locales[0]
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/**
 * 秒级时间戳 → 文本。0 与负值按「无时间」返回空串。
 * [zoneId] 显式传入是为了让纯函数测试能固定时区；运行期默认取当前系统时区。
 */
internal fun formatEpochSeconds(
    seconds: Long,
    formatter: DateTimeFormatter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (seconds <= 0L) return ""
    return runCatching {
        formatter.format(Instant.ofEpochSecond(seconds).atZone(zoneId))
    }.getOrDefault("")
}

/** 毫秒级时间戳 → 文本；历史/追更接口返回毫秒。 */
internal fun formatEpochMillis(
    millis: Long,
    formatter: DateTimeFormatter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = runCatching {
    formatter.format(Instant.ofEpochMilli(millis).atZone(zoneId))
}.getOrDefault("")
