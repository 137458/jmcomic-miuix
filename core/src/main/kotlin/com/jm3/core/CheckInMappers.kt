package com.jm3.core

import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicks
import com.jm3.core.model.WeeklyPicksDetail
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInStatus
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksDetail
import io.github.jukomu.jmcomic.api.model.JmWeeklyPicksList

/** 签到状态：signedDays/totalDays 由 record 记录扁平统计；未签到/无标记不算签到。 */
fun JmDailyCheckInStatus.toDailyCheckInStatus(): DailyCheckInStatus {
    val items = record.orEmpty().flatten()
    return DailyCheckInStatus(
        dailyId = dailyId,
        eventName = eventName.orEmpty(),
        currentProgress = currentProgress.orEmpty(),
        signedDays = items.count { it.signed == true },
        totalDays = items.size,
        days = items.mapNotNull { item ->
            // date 既可能是 "01"，也可能是 "2026-10-01"（两种都实测出现），无法解析就跳过
            val day = item.date.parseCheckInDayOfMonth() ?: return@mapNotNull null
            CheckInDay(dayOfMonth = day, signed = item.signed == true, bonus = item.bonus)
        },
    )
}

/**
 * 解析签到记录的 date 为当月日号：
 * 支持 "01"、"1"、"11-12"、"11/12"、"2026-10-01"、"2026/10/1" 及带空格/T 时间后缀格式；越界或无法解析返回 null。
 */
internal fun String?.parseCheckInDayOfMonth(): Int? {
    val text = this?.trim().orEmpty()
    if (text.isEmpty()) return null
    val datePart = text.substringBefore(' ').substringBefore('T').trim()
    if (datePart.isEmpty()) return null
    datePart.toIntOrNull()?.let { return it.takeIf { day -> day in 1..31 } }
    val parts = datePart.split('-', '/', '.')
    return when (parts.size) {
        3 -> {
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            if (year >= 1 && month in 1..12 && day in 1..31) day else null
        }

        2 -> {
            val month = parts[0].toIntOrNull() ?: return null
            val day = parts[1].toIntOrNull() ?: return null
            if (month in 1..12 && day in 1..31) day else null
        }

        else -> null
    }
}

/** 每周必看期数列表；null 分类按空表处理。 */
fun JmWeeklyPicksList.toWeeklyPicks(): WeeklyPicks = WeeklyPicks(
    periods = categories().orEmpty().map { category ->
        WeeklyPeriod(
            id = category.id().orEmpty(),
            title = category.title().orEmpty(),
            time = category.time().orEmpty(),
        )
    },
)

/** 每周必看某期结果；封面与列表页走同一个入口。 */
fun JmWeeklyPicksDetail.toWeeklyPicksDetail(covers: CoverResolver = CoverResolver.Unready): WeeklyPicksDetail =
    WeeklyPicksDetail(
        total = total(),
        items = list().orEmpty().map { it.toSearchResult(covers) },
    )
