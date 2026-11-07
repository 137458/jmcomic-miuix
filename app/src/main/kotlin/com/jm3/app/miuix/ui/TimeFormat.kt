package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 按「当前界面语言」而不是 `Locale.getDefault()` 建格式化器。
 *
 * 应用内切语言走的是资源与配置的 locale，进程级 `Locale.getDefault()` 未必同步；
 * 顶层 `SimpleDateFormat` 常量一旦在启动时捕获 locale，切语言后列表里的日期会继续
 * 按旧语言排版（阿拉伯数字形态、月份写法都会错）。
 *
 * `SimpleDateFormat` 非线程安全，但这里按组合持有、只在主线程使用，`remember` 以
 * locale 为键，切语言时自动重建。
 */
@Composable
internal fun rememberDateFormatter(pattern: String = "yyyy-MM-dd HH:mm"): SimpleDateFormat {
    // 只读配置里的 locale（可观察），不读 Locale.getDefault()：后者不属于组合状态，
    // 切语言后不会触发重组。活动配置的 locale 列表不会为空，兜底仅防越界
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) Locale.ROOT else locales[0]
    return remember(pattern, locale) { SimpleDateFormat(pattern, locale) }
}
